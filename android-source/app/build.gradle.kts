plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.jeaxlabs.os"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.jeaxlabs.os"
        minSdk = 26
        targetSdk = 35
        versionCode = 140
        versionName = "1.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
