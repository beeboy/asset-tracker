plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 위젯 + 앱·위젯 공용 (아이폰 WidgetShared·Widgets 자리). 앱 모듈이 이 모듈을 쓴다
// 공용 모델·저장·시세(core/)는 위젯도 읽어야 해서 이 모듈에 둔다 (패키지는 com.naeilo.app.core 그대로)
android {
    // 출시판 빌드마다 도는 lint 검사(빌드 시간의 대부분)를 끈다. 필요하면 ./gradlew lint 로 따로 돌린다
    lint { checkReleaseBuilds = false }
    namespace = "com.naeilo.widget"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true; buildConfig = false }
}
// AGP 9 새 DSL 로 자료 폴더를 넘긴다 (옛 sourceSets 접근자는 형 변환에서 깨진다)
extensions.getByType<com.android.build.api.dsl.LibraryExtension>().sourceSets.getByName("main").res
    .directories.add(layout.buildDirectory.dir("iosRes").get().asFile.path)

// 아이폰 위젯 그림 중 쓰는 것만 drawable-nodpi 로: 인물 기본·포즈·raise·spread, 쉼터 고양이 (투명·틴트용 _tint 와 CharItem 물건 그림은 안 쓴다)
val copyWidgetImages by tasks.registering(Copy::class) {
    from("../../ios-app/Widgets/Assets.xcassets") { include("w_*.imageset/*.png"); exclude("w_*_tint.imageset/**", "w_item_*.imageset/**") }
    eachFile { path = "drawable-nodpi/" + file.parentFile.name.removeSuffix(".imageset") + ".png" }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("iosRes"))
}
tasks.named("preBuild") { dependsOn(copyWidgetImages) }

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.serialization.json)
    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime)
    implementation(libs.activity.compose)
}
