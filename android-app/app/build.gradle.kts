plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // 출시판 빌드마다 도는 lint 검사(빌드 시간의 대부분)를 끈다. 필요하면 ./gradlew lint 로 따로 돌린다
    lint { checkReleaseBuilds = false }
    namespace = "com.naeilo.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.naeilo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 서명 키가 정해지기 전까지 에뮬레이터 실행용으로 디버그 키를 쓴다
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = false }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("iosAssets").get().asFile)
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("iosRes").get().asFile)
}

// iOS 원본 자료 중 앱이 읽는 것만 빌드 때 복사한다 (저장소에 같은 파일을 두 벌 두지 않는다)
val copyIosAssets by tasks.registering(Copy::class) {
    from("../../ios-app/Naeilo/Resources") { include("data_en.json", "drnk_ratio.json", "krx_stocks.json", "lines_ko.json", "lines_app_en.json", "story_ko.json", "story_en.json", "earnings_seed.json") }
    from("../../ios-app/WidgetShared") { include("lines_en.json") }
    // 아이폰 번들과 같은 시세 자료 (project.yml: data/quotes.json·stats.json·prices). 앱이 아직 받지 못했을 때만 쓴다
    from("../../data") { include("quotes.json", "stats.json") }
    from("../../data/prices") { include("*.json"); into("prices") }
    into(layout.buildDirectory.dir("iosAssets"))
}
// iOS 그림(xcassets) 중 화면이 쓰는 묶음만 drawable-nodpi 로 (이름 = imageset 이름). 도트 그림은 보간 없이 키운다 (Pixel)
// 새 묶음이 필요하면 접두어를 더한다: spr_·sil_ 인물, mo_ 동작, iconprev_ 앱 아이콘 미리보기, drnk_ 샘플 종목 로고, me_ 내 캐릭터 부품, bg_ 쉼터 방, art_ 방·물건·서재 장 그림
val iosImagePrefixes = listOf("spr_", "sil_", "mo_", "iconprev_", "drnk_", "me_", "bg_", "art_")
val copyIosImages by tasks.registering(Copy::class) {
    from("../../ios-app/Naeilo/Assets.xcassets") { iosImagePrefixes.forEach { include("$it*.imageset/*.png") } }
    // 다크 그림(<이름>_dark.png)은 제 이름 그대로 둔다 (묶음 이름으로 바꾸면 밝은 그림과 겹친다)
    eachFile { path = "drawable-nodpi/" + (if (file.name.endsWith("_dark.png")) file.name else file.parentFile.name.removeSuffix(".imageset") + ".png") }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("iosRes"))
}
tasks.named("preBuild") { dependsOn(copyIosAssets, copyIosImages) }
// JSON 서로 읽기 시험: Kotlin 이 다시 쓴 JSON 을 저장소 밖에 남긴다 (tools/json_parity/parity.py check 가 Swift 로 읽는다)
tasks.withType<Test>().configureEach {
    systemProperty("parity.out", File(System.getProperty("user.home"), "Library/Caches/naeilo-android/json_parity").path)
    // 실제 시세 받기 시험은 -PnaeiloLive=1 일 때만 (LiveFeedTest)
    systemProperty("naeilo.live", providers.gradleProperty("naeiloLive").getOrElse(""))
    inputs.dir("../../tools/json_parity/fixtures")
    // 숫자 맞추기 입력(parity.py gen 이 저장소 밖에 쓴다)이 바뀌면 다시 돌게
    val np = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity")
    if (np.isDirectory) inputs.files(fileTree(np) { include("*/input.json") })
}

dependencies {
    implementation(project(":widget"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.serialization.json)
    implementation(libs.billing)
    testImplementation(kotlin("test"))
}
