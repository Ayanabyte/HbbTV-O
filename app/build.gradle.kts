plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ayanabyte.hbbtvo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ayanabyte.hbbtvo.live"
        minSdk = 26
        targetSdk = 30
        versionCode = 14
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
