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
}

dependencies {
    implementation(project(":widget"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.serialization.json)
}
