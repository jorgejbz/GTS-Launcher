import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing comes from llave-firma/keystore.properties next to launcher/ (kept out of git).
// storeFile inside it is relative to that file.
val keystoreFile = listOf(rootProject.file("../llave-firma/keystore.properties"), rootProject.file("keystore.properties"))
    .firstOrNull { it.exists() }
val keystoreProps = Properties().apply {
    keystoreFile?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.jajua.gts"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.jajua.gts"
        // Android 8.0+: most Android head units run 8.1, 9 or 10. Newer APIs are guarded in code.
        minSdk = 26
        targetSdk = 36
        // versionCode only ever grows (Android refuses downgrades).
        versionCode = 101
        versionName = "1.1.0-dev"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = File(keystoreFile!!.parentFile, keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Same key as releases, so development builds and published ones install over each other.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        release {
            optimization {
                enable = true
            }
            // Without keystore.properties fall back to the debug key so the APK still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
