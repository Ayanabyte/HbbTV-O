plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ayanabyte.hbbtvo"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.ayanabyte.hbbtvo"
        minSdk = 26
        targetSdk = 30
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
