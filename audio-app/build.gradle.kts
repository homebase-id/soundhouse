plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "id.homebase.audio.resources"
    nameOfResClass = "AR"
    generateResClass = auto
}

kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets.all {
        languageSettings.apply {
            optIn("kotlin.uuid.ExperimentalUuidApi")
            optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            optIn("kotlin.time.ExperimentalTime")
        }
    }

    android {
        namespace = "id.homebase.audio.shared"
        compileSdk = libs.versions.android.targetSdk.get().toInt()
        compileSdkExtension = 19
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        withHostTest {}
    }

    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AudioApp"
            isStatic = true
        }
    }

    // Same as the copied modules: the test binary links SQLDelight's sqliter, which needs libsqlite3.
    iosSimulatorArm64().binaries
        .getTest(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG)
        .linkerOpts("-lsqlite3")

    sourceSets {
        commonMain.dependencies {
            api(project(":homebase-api"))
            api(project(":homebase-common"))
            implementation(project(":homebase-auth"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.resources)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.kermit)
            implementation(libs.okio)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.mock)
        }
    }

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}

val liveTestPackage = "id.homebase.audio.live.*"

tasks.withType<Test>().matching { it.name == "jvmTest" }.configureEach {
    filter { excludeTestsMatching(liveTestPackage) }
}

// Talks to a real identity with ~/.config/homebase-audio-test/session.json; skipped without it and
// never part of jvmTest.
val liveTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Round-trips against the live Audio drive of the test identity."
    val testCompilation = kotlin.jvm().compilations.getByName("test")
    testClassesDirs = testCompilation.output.classesDirs
    classpath = files(testCompilation.output.allOutputs, testCompilation.runtimeDependencyFiles)
    useJUnit()
    filter { includeTestsMatching(liveTestPackage) }
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
