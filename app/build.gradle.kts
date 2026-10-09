plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Auf GitHub zählt jeder Build die Versionsnummer hoch, damit Updates sauber installiert werden.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
val keystoreFile: String? = env("KEYSTORE_FILE")

android {
    namespace = "de.stickertresor.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.stickertresor.app"
        minSdk = 30
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = env("KEYSTORE_PASSWORD")
                keyAlias = env("KEY_ALIAS") ?: "tresor"
                keyPassword = env("KEY_PASSWORD") ?: env("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
