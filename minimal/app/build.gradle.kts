plugins { id("com.android.application") }
android {
    namespace = "com.ayanabyte.o.lovestv.minimal"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ayanabyte.o.lovestv.minimal5317"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "0.0.1"
        ndk { abiFilters += "armeabi-v7a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3") }
