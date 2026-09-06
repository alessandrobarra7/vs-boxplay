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
        versionCode = 3
        versionName = "0.2.1"

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

tasks.configureEach {
    if (name == "preReleaseBuild" || name == "assembleRelease" || name == "bundleRelease") {
        dependsOn("validateReleaseSigning")
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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
