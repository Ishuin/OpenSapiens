plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.opensapien.core.transcription"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // Vosk on-device ASR. JNA must be the @aar artifact (bundles Android natives).
    api(libs.vosk.android)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    testImplementation(libs.junit)
}
