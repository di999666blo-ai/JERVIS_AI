plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    val releaseStorePath = System.getenv("BOBBY_KEYSTORE_PATH")
    signingConfigs {
        if (!releaseStorePath.isNullOrBlank()) {
            create("bobbyRelease") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("BOBBY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BOBBY_KEY_ALIAS")
                keyPassword = System.getenv("BOBBY_KEY_PASSWORD")
            }
        }
    }
    namespace = "ai.diablo.bobby"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.diablo.bobby"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("BOBBY_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    buildTypes.getByName("debug") {
        if (!releaseStorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("bobbyRelease")
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
