plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningVariables = mapOf(
    "RELEASE_STORE_FILE" to System.getenv("RELEASE_STORE_FILE"),
    "RELEASE_STORE_PASSWORD" to System.getenv("RELEASE_STORE_PASSWORD"),
    "RELEASE_KEY_ALIAS" to System.getenv("RELEASE_KEY_ALIAS"),
    "RELEASE_KEY_PASSWORD" to System.getenv("RELEASE_KEY_PASSWORD"),
)
val hasReleaseSigningConfig = releaseSigningVariables.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.boxplay"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.boxplay"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The keystore itself is NEVER read from this repo — see
    // docs/BOXPLAY_PLANO_COMPRA_UNICA_PLAYSTORE_V1.txt section 4 ("SEGURANCA").
    // These four values only exist as environment variables at build time
    // (exported locally before `./gradlew bundleRelease`, or injected as
    // GitHub Actions secrets by .github/workflows/build-release-aab.yml).
    // Debug builds do not need these values; release artifacts for real
    // testing/publishing must provide all four.
    val releaseStoreFile = releaseSigningVariables.getValue("RELEASE_STORE_FILE")
    val releaseStorePassword = releaseSigningVariables.getValue("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = releaseSigningVariables.getValue("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = releaseSigningVariables.getValue("RELEASE_KEY_PASSWORD")

    if (hasReleaseSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Fails when release signing environment variables are missing."

    doLast {
        if (!hasReleaseSigningConfig) {
            val missing = releaseSigningVariables
                .filterValues { it.isNullOrBlank() }
                .keys
                .joinToString(", ")
            throw org.gradle.api.GradleException(
                "Release signing is incomplete. Configure these environment variables: $missing",
            )
        }
    }
}

// Only wire validateReleaseSigning automatically when the CI env vars are the
// thing actually doing the signing. When you build a signed release locally
// through Android Studio's own "Generate Signed Bundle / APK" wizard, Studio
// injects its own signing info for that one build and never touches
// RELEASE_STORE_FILE/etc, so forcing this check on every assembleRelease
// would fail local IDE builds for no reason. CI still runs the check
// explicitly (see .github/workflows/build-release-aab.yml), which is the
// build that actually depends on those environment variables.
if (hasReleaseSigningConfig) {
    tasks.configureEach {
        if (name == "preReleaseBuild" || name == "assembleRelease" || name == "bundleRelease") {
            dependsOn("validateReleaseSigning")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    val lifecycleVersion = "2.11.0"
    val media3Version = "1.11.0"
    val billingVersion = "9.1.0"

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("com.android.billingclient:billing-ktx:$billingVersion")

    // Suporte a pacotes multipista distribuídos como .rar (seção de
    // importação de stems do editor multipista). O formato RAR é
    // proprietário — ao contrário do .zip, não existe suporte nativo no
    // Android/Kotlin ("java.util.zip" só cobre ZIP), então esta é a única
    // dependência nova do módulo multipista, adicionada especificamente
    // para isso.
    implementation("com.github.junrar:junrar:7.5.5")

    // Usado pelo fluxo de "Exportar para Box": deixa o usuário escolher (ou
    // criar) a pasta de destino do áudio mixado via o seletor de pasta
    // padrão do Android (SAF), tanto no armazenamento do celular quanto em
    // provedores de nuvem como o Google Drive.
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Encoder MP3 real (wrapper Android/JNI da libmp3lame) usado pelo
    // mixdown do editor multipista — o Android só tem DECODER de MP3
    // embutido (MediaCodec/MediaMuxer não geram .mp3), então gerar um .mp3
    // de verdade exige essa dependência. Ver MultitrackMixdownEngine.kt e o
    // repositório JitPack em settings.gradle.kts.
    //
    // Excluindo com.android.support:appcompat-v7: essa lib de 2017 usa a
    // Android Support Library antiga só para o seu próprio app de demo —
    // o BoxPlay usa apenas as classes puras de encoding (AndroidLame/
    // LameBuilder), nunca nenhuma Activity/tela dela. Sem essa exclusão, o
    // Jetifier gera uma cópia jetificada de androidx.vectordrawable(-animated)
    // que colide com a versão real já usada pelo Compose/Material, e o build
    // falha com "Namespace 'androidx.vectordrawable' is used in multiple
    // modules and/or libraries" no merge do Manifest.
    implementation("com.github.naman14:TAndroidLame:1.1") {
        exclude(group = "com.android.support", module = "appcompat-v7")
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
