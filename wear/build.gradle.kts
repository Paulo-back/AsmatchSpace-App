plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "br.projetoasthmaspace.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.fmu.projetoasthmaspace"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.play.services.wearable)

    // Wear OS UI
    implementation("androidx.wear:wear:1.3.0")

    // Notificações
    implementation("androidx.core:core:1.13.1")
}