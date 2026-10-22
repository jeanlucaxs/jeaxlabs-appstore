plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseVersion = java.util.Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

val releaseStoreFile = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "br.com.jeaxlabs.os"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.jeaxlabs.os"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersion.getProperty("VERSION_CODE").toInt()
        versionName = releaseVersion.getProperty("VERSION_NAME")
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("officialRelease") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("officialRelease")
            }
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


dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
}
