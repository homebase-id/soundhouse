import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val versionProps = Properties()
versionProps.load(rootProject.file("gradle/version.properties").inputStream())

android {
    namespace = "id.homebase.audio"
    compileSdk {
        version = release(libs.versions.android.targetSdk.get().toInt())
    }
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "id.homebase.audio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionProps.getProperty("version.code.base").toInt()
        versionName = versionProps.getProperty("version.name")
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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            // Phones and the emulator only: the copied ffmpeg-kit and SQLCipher ship ~40 MB of .so per ABI.
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kermit)
    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs.compose)
}
