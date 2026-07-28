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

// sherpa-onnx ships no Maven artifact, only a GitHub release AAR (~47 MB).
// Fetch it on demand so the binary never has to live in git.
val sherpaVersion = libs.versions.sherpaOnnx.get()
val sherpaAar = rootProject.layout.projectDirectory
    .file("libs/sherpa-onnx-$sherpaVersion.aar").asFile

val downloadSherpaAar by tasks.registering {
    description = "Downloads the sherpa-onnx Android AAR if absent."
    outputs.file(sherpaAar)
    onlyIf { !sherpaAar.exists() }
    doLast {
        sherpaAar.parentFile.mkdirs()
        val url =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
                "v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        logger.lifecycle("Fetching sherpa-onnx $sherpaVersion AAR (~47 MB)…")
        val tmp = File(sherpaAar.parentFile, sherpaAar.name + ".part")
        uri(url).toURL().openStream().use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        check(tmp.length() > 1_000_000) { "sherpa AAR download truncated: ${tmp.length()} bytes" }
        tmp.renameTo(sherpaAar)
    }
}

tasks.named("preBuild") { dependsOn(downloadSherpaAar) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // On-device ASR. `api` so :app inherits the native libs at packaging time.
    api(group = "", name = "sherpa-onnx-$sherpaVersion", ext = "aar")
    testImplementation(libs.junit)
}
