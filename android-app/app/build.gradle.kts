plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
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
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("iosAssets"))
}

// iOS 원본 자료 중 앱이 읽는 것만 빌드 때 복사한다 (저장소에 같은 파일을 두 벌 두지 않는다)
val copyIosAssets by tasks.registering(Copy::class) {
    from("../../ios-app/Naeilo/Resources") { include("data_en.json", "drnk_ratio.json", "krx_stocks.json") }
    from("../../ios-app/WidgetShared") { include("lines_en.json") }
    // 아이폰 번들과 같은 시세 자료 (project.yml: data/quotes.json·stats.json·prices). 앱이 아직 받지 못했을 때만 쓴다
    from("../../data") { include("quotes.json", "stats.json") }
    from("../../data/prices") { include("*.json"); into("prices") }
    into(layout.buildDirectory.dir("iosAssets"))
}
tasks.named("preBuild") { dependsOn(copyIosAssets) }
// JSON 서로 읽기 시험: Kotlin 이 다시 쓴 JSON 을 저장소 밖에 남긴다 (tools/json_parity/parity.py check 가 Swift 로 읽는다)
tasks.withType<Test>().configureEach {
    systemProperty("parity.out", File(System.getProperty("user.home"), "Library/Caches/naeilo-android/json_parity").path)
    // 실제 시세 받기 시험은 -PnaeiloLive=1 일 때만 (LiveFeedTest)
    systemProperty("naeilo.live", providers.gradleProperty("naeiloLive").getOrElse(""))
    inputs.dir("../../tools/json_parity/fixtures")
}

dependencies {
    implementation(project(":widget"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.serialization.json)
    testImplementation(kotlin("test"))
}
