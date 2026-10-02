
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "id.homebase.auth"
        compileSdk = libs.versions.android.targetSdk.get().toInt()
        compileSdkExtension = 19
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "homebase-authKit"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":homebase-api"))
            implementation(project(":homebase-common"))

            implementation(libs.kermit)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.resources)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.immutableCollections)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.ui.tooling)
        }
        jvmMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.jetbrains.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            // Dispatchers.Main for the Compose UI tests.
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

// `withHostTest {}` is enabled above so AGP 9.2 stops warning about commonTest
// with no Android host-test runner. Compose UI tests in commonTest use a
// Skiko-backed implementation that doesn't compose against the mocked Android
// host-test classpath, so disable the task until those tests are migrated
// to jvmTest/.
tasks.matching { it.name == "testAndroidHostTest" }.configureEach {
    enabled = false
}
