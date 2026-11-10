plugins {
    alias(libs.plugins.android.application)
    // AGP 9 ma wbudowaną obsługę Kotlina – nie trzeba dodawać pluginu kotlin-android
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.myplayer"
        minSdk = 29
        // Celowo 36, a nie 37: przy targetSdk 37 Android 17 wymaga od usługi pierwszoplanowej
        // uprawnień "while-in-use", których usługa startowana z tła (po połączeniu Bluetooth)
        // zwykle nie dostaje – dźwięk byłby wtedy po cichu blokowany. Szczegóły w README.md.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Stały klucz podpisu wersji debug (publicznie znany, tylko do własnych testów – nie do Google Play).
    // Dzięki niemu każda wersja zbudowana na dowolnym komputerze lub w GitHub Actions ma ten sam podpis,
    // więc nową wersję można zainstalować "na wierzch" bez odinstalowywania poprzedniej.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
