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
        // Necessário para com.github.naman14:TAndroidLame (encoder MP3 real
        // via LAME, usado pelo mixdown do editor multipista — ver
        // app/build.gradle.kts e MultitrackMixdownEngine.kt). O Android não
        // tem encoder de MP3 nativo (só decoder), então essa é a única forma
        // de gerar um .mp3 de verdade sem depender de servidor externo.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "VSPLAY BARRA7"
include(":app")
