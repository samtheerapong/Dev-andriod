plugins {
    // AGP 9 compiles Kotlin natively; no separate kotlin-android plugin needed.
    id("com.android.application")
}

android {
    namespace = "com.example.carbrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.carbrowser"
        // Presentation on a private VirtualDisplay + modern WebView behave best on Android 10+.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal sideload only: sign release with the debug key so it installs without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    // Car App Library is not needed: the app runs on Android Auto as a parked app
    // (plain Activity). The earlier Surface/VirtualDisplay version lives in archive/surface-hack.
    implementation("androidx.core:core-ktx:1.13.1")
}
