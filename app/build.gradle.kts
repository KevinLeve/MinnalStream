plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kevinleve.minnalstream"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kevinleve.minnalstream"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-hotspot-preview"
    }
}
