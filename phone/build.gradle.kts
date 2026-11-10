plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.admurs.oyunaltyazi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.admurs.oyunaltyazi"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Sabit anahtar: her derleme aynı imzayla çıkar, yeni sürüm eskisinin üstüne kurulur.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(project(":core"))

    // Cihazda çalışan yazı tanıma (Latin alfabesi, model APK'nın içinde)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Cihazda çalışan çeviri (İngilizce→Türkçe modeli ilk kullanımda indirilir)
    implementation("com.google.mlkit:translate:17.0.3")

    testImplementation("junit:junit:4.13.2")
}
