plugins { id("com.android.application") }
android {
    namespace = "com.ayanabyte.o.lovestv"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ayanabyte.o.lovestv.lab7"
        minSdk = 26
        targetSdk = 30
        versionCode = 7
        versionName = "0.1.6-test"
        ndk { abiFilters += "armeabi-v7a" }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
