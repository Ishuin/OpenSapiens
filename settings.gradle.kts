pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx publishes no Maven artifact; the AAR is fetched from its
        // GitHub release by the :core:transcription `downloadSherpaAar` task.
        flatDir { dirs("$rootDir/libs") }
    }
}

rootProject.name = "open_sapien"

include(":app")
include(":wear")
include(":core:recording")
include(":core:transcription")
include(":core:data")
include(":core:sync")
