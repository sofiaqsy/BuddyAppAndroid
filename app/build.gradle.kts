plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.buddy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vrcoffe.buddyapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"https://buddy-core-504b393f8333.herokuapp.com/v1/\"")
        // Client "Buddy App Web" (tipo Aplicación web) del proyecto BuddyApp —
        // GetGoogleIdOption.serverClientId EXIGE un cliente web, nunca uno tipo
        // Android (usar el Android daba 28444). El backend valida el aud contra
        // GOOGLE_CLIENT_IDS (debe incluir este ID).
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"975826303754-2c8f1c4mjif0f0nn56gappm2l8k6h99m.apps.googleusercontent.com\"")

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.location)

    // Mapa del trip (espejo de TripDetailView/MapKit en iOS) — OpenStreetMap:
    // sin API key ni cuenta de Google Cloud, tiles libres con atribución.
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Firebase Cloud Messaging
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // ML Kit Subject Segmentation — sticker (subject lift) del Trip Book,
    // equivalente Android del VNGenerateForegroundInstanceMaskRequest de iOS
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")

    testImplementation(libs.junit)
}
