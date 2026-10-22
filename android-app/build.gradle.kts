plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// 빌드 결과물은 저장소 밖에 둔다 (iCloud·git 에 안 섞이게)
val outRoot = File(System.getProperty("user.home"), "Library/Caches/naeilo-android")
allprojects { layout.buildDirectory.set(File(outRoot, name)) }
