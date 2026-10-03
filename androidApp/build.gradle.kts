import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.playPublisherPlugin)
}

val versionProps = Properties()
versionProps.load(rootProject.file("gradle/version.properties").inputStream())

play {
    track.set("internal")
    serviceAccountCredentials.set(file("../google-play-key.json"))
}

android {
    namespace = "id.homebase.soundhouse"
    compileSdk {
        version = release(libs.versions.android.targetSdk.get().toInt())
    }
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "id.homebase.soundhouse"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toInt()
            ?: versionProps.getProperty("version.code.base").toInt()
        versionName = project.findProperty("VERSION_NAME") as String?
            ?: versionProps.getProperty("version.name")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

    signingConfigs {
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = file("../buildsystem/debug.keystore")
            storePassword = "android"
        }
        // CI decodes the upload keystore from a secret; without one (local builds) release and dev
        // fall back to the debug key, so a sideloaded build still installs over the last one.
        val keystorePath = System.getenv("SIGNING_KEYSTORE_FILE_PATH")
        create("upload") {
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("upload")
        }
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            matchingFallbacks += "release"
        }
        debug {
            applicationIdSuffix = ".debug"
            // Phones and the emulator only: SQLCipher ships several MB of .so per ABI.
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// ktor-server-core pulls in kotlin-reflect for config-file module loading, which the stream server
// doesn't use; on the classpath it makes every Ktor typeOf() go through full reflection (~2 s at startup).
configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
}

dependencies {
    implementation(project(":audio-app"))

    implementation(libs.androidx.appcompat)
    // Theme.Material3.DayNight.NoActionBar in the manifest.
    implementation(libs.android.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kermit)
    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs.compose)

    baselineProfile(project(":baselineprofile"))
}
