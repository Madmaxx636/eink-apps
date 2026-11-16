import java.util.Properties

plugins {
    id("com.android.application")
}

@Suppress("UNCHECKED_CAST")
val keystoreProps = rootProject.extra["keystoreProps"] as Properties

android {
    namespace = "app.eink.notes"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.eink.inkpad"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = File(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

dependencies {
    implementation(project(":core"))
}
