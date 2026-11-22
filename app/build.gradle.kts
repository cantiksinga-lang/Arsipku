plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alhafidz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alhafidz.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Keystore dari environment (diisi oleh GitHub Actions). Kalau kosong -> debug key (build lokal).
    val ksPath = System.getenv("KEYSTORE_PATH")
    if (ksPath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/*.version", "/META-INF/*.kotlin_module",
                "/META-INF/com/android/build/gradle/app-metadata.properties",
                "DebugProbesKt.bin", "kotlin-tooling-metadata.json", "/kotlin/**"
            )
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// ML Kit bundled text recognition untuk OCR lokal/offline.
dependencies {
    // Bundled ML Kit Text Recognition: OCR diproses lokal di perangkat.
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
