plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.studiogyro.theiptvapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.studiogyro.theiptvapp"
        minSdk = 21
        targetSdk = 35
        // le numéro de version vient de la compilation en ligne (-PvCode=<numéro>) : il augmente à chaque version
        val vCode = (project.findProperty("vCode") as String?)?.toIntOrNull() ?: 1
        versionCode = vCode
        versionName = "0.1.$vCode"
    }

    // clé de signature fixe : sans elle, Android refuse d'installer une version par-dessus la précédente
    signingConfigs {
        create("fixed") {
            storeFile = file("theiptvapp.jks")
            storePassword = "theiptvapp-sideload"
            keyAlias = "theiptvapp"
            keyPassword = "theiptvapp-sideload"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            // version « release » : code optimisé (R8), beaucoup plus fluide que la version de débogage
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
