import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    applyDefaultHierarchyTemplate()

    // ✅ GLOBAL opt-ins for ALL source sets & targets
    sourceSets.all {
        languageSettings.apply {
            optIn("kotlin.uuid.ExperimentalUuidApi")
            optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            optIn("kotlinx.serialization.ExperimentalSerializationApi")
            optIn("kotlin.time.ExperimentalTime")
            optIn("dev.whyoleg.cryptography.DelicateCryptographyApi")
        }
    }

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    android {
        namespace = "id.homebase.api"
        compileSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        withHostTest {}
        // Enables `src/androidInstrumentedTest/` for emulator-on-device tests
        // (currently used by CompressVideoAndroidInstrumentedTest, which is
        // @Ignore'd until CI emulator infra lands). Locally runnable with:
        //   ./gradlew homebase-api:connectedAndroidTest
        withDeviceTest {}
    }

    jvm() {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "homebase-api"
            isStatic = true
        }
    }

    // Cinterop the bundled FFmpegKit xcframework into the iOS simulator *test* compilation
    // only so FfmpegDecoderCommonTest can stand up a real FFmpegKitBridge implementation
    // (see src/nativeTest/.../TestFFmpegKitBridge.kt) instead of stubbing the iOS leg green.
    // The xcframework is checked in under homebase-api/libs/ — the same artifact iosApp's
    // pbxproj already references — so this is purely a test-link concern and doesn't touch
    // the production framework export above. We only wire iosSimulatorArm64: we never run
    // device-target tests (no `iosArm64Test` job exists), so configuring cinterop + linker
    // for iosArm64's test binary would be dead-weight that confuses the next reader.
    // ffmpegkit-bundled.xcframework ships ffmpegkit + seven sibling FFmpeg libraries
    // (libav*, libsw*). Each is a separate .xcframework; ffmpegkit.framework's binary has
    // `@rpath/libavdevice.framework/libavdevice` (etc.) baked in, so the test binary needs
    // *every* sibling's simulator-slice directory on its rpath at runtime. Production iOS
    // gets these from iosApp's pbxproj "Embed Frameworks" step.
    val ffmpegKitBundleRoot = project.projectDir
        .resolve("libs/ffmpegkit-bundled.xcframework")
    val ffmpegKitSimulatorFrameworkDirs = listOf(
        "ffmpegkit",
        "libavcodec",
        "libavdevice",
        "libavfilter",
        "libavformat",
        "libavutil",
        "libswresample",
        "libswscale",
    ).map { name ->
        ffmpegKitBundleRoot.resolve("$name.xcframework/ios-arm64_x86_64-simulator").absolutePath
    }
    val ffmpegKitFrameworkDir = ffmpegKitSimulatorFrameworkDirs.first()  // ffmpegkit slice

    iosSimulatorArm64().compilations.getByName("test").cinterops.create("ffmpegkit") {
        defFile(project.file("src/nativeTest/cinterop/ffmpegkit.def"))
        compilerOpts("-F$ffmpegKitFrameworkDir")
    }
    // dyld needs a search path at runtime as well; the test binary is started outside
    // iosApp's `Embed Frameworks` step, so without explicit rpath dyld can't locate
    // ffmpegkit.framework OR its bundled FFmpeg-lib siblings. Baking the absolute paths is
    // OK because the test binary only runs in the build environment and never ships.
    //
    // Production iOS app links libsqlite3 via iosApp's Xcode project — for the gradle
    // test binary we have to wire it in explicitly so SQLDelight's sqliter cinterop has its
    // underlying symbols at link time. Previously unnoticed because iosSimulatorArm64Test
    // was never run in CI (test.yml has been disabled).
    val testBinaryLinkerOpts = buildList {
        add("-F$ffmpegKitFrameworkDir")
        add("-framework"); add("ffmpegkit")
        ffmpegKitSimulatorFrameworkDirs.forEach { add("-rpath"); add(it) }
        add("-lsqlite3")
    }
    iosSimulatorArm64().binaries
        .getTest(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG)
        .linkerOpts(testBinaryLinkerOpts)

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        // Shared Skia-backed implementations for the targets that bundle skiko:
        // Desktop/JVM, iOS/native, and Web/wasmJs. Android is intentionally excluded —
        // it uses android.graphics instead. Lets ImageUtils live in one place rather than
        // three near-identical per-platform copies (only convertHeicToJpeg stays per-platform).
        val skiaMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(skiaMain)
        nativeMain.get().dependsOn(skiaMain)

        commonMain.dependencies {
            implementation(libs.atomicfu)
            implementation(libs.kermit)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.encoding)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.cryptography.random)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.koin.core)
            // Compose runtime + ui: @Immutable, snapshot state, ImageBitmap.
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.filekit.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.coil3)
            implementation(libs.okio)
            // CommonMark AST parser for markdownToPlainPreview (MarkdownPlain.kt).
            // Same engine the chat renderer (mikepenz) and editor (richeditor)
            // use, so the preview strip grammar mirrors the rendered output.
            implementation(libs.jetbrains.markdown)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.okio.fakefilesystem)
        }

        // Tests that use blocking coroutine APIs (runBlocking et al.) — JVM + native only;
        // wasmJs's kotlinx-coroutines has no blocking variants. Keeps the wasmJs test target
        // compilable for the cross-platform FfmpegDecoderCommonTest without losing JVM/iOS
        // coverage of these tests.
        val jvmAndNativeTest by creating { dependsOn(commonTest.get()) }
        jvmTest.get().dependsOn(jvmAndNativeTest)
        nativeTest.get().dependsOn(jvmAndNativeTest)
        // androidHostTest is in the "test" source-set tree (runs on the host JVM with the
        // Android framework stubbed), so it can depend on jvmAndNativeTest. androidDeviceTest
        // is in the "instrumented" tree and KGP refuses cross-tree dependsOn — so the
        // blocking-coroutine tests in jvmAndNativeTest aren't reachable from device tests.
        // If any of them ever genuinely needs device-side coverage, copy it directly into
        // androidDeviceTest rather than trying to wire the inheritance.
        getByName("androidHostTest").dependsOn(jvmAndNativeTest)
        androidMain.dependencies {
            implementation(libs.androidx.exifinterface)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.android.database.sqlcipher)
            implementation(libs.ffmpeg.kit)
            implementation(libs.smart.exception.java)
            // MP4 atom-tree manipulation. Used by Mp4LocationStripper to drop
            // EXIF / GPS location atoms from camera-recorded MP4s when the
            // input passes through compressVideo's already-optimal check
            // without re-encoding (re-encode naturally drops the atoms).
            implementation(libs.mp4parser.isoparser)
            implementation(libs.androidsvg)
        }
        nativeMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.sqlite.driver.get().toString()) {
                exclude(group = "org.xerial", module = "sqlite-jdbc")
            }
            implementation(libs.sqlite.jdbc.crypt)

            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)


        }

        // Provide Skia native binaries for JVM image tests (platform-specific)
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val desktopDep = when {
            osName.contains("win") -> libs.jetbrains.compose.desktop.jvm.windows.x64
            osName.contains("mac") && osArch.contains("aarch64") -> libs.jetbrains.compose.desktop.jvm.macos.arm64
            osName.contains("mac") -> libs.jetbrains.compose.desktop.jvm.macos.x64
            osArch.contains("aarch64") || osArch.contains("arm64") -> libs.jetbrains.compose.desktop.jvm.linux.arm64
            else -> libs.jetbrains.compose.desktop.jvm.linux.x64
        }
        jvmTest.dependencies {
            implementation(desktopDep)
        }
        // Android host tests run on the JVM with android.jar stubs — the real
        // AndroidSqliteDriver would throw "Stub!" at runtime, so we use the
        // JDBC driver the same way jvmMain does.
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver.get().toString()) {
                exclude(group = "org.xerial", module = "sqlite-jdbc")
            }
            implementation(libs.sqlite.jdbc.crypt)
        }
        // Android instrumented (on-device) tests for things that need real
        // platform implementations — e.g. CompressVideoAndroidInstrumentedTest
        // exercises the MediaCodec transcode path, which needs real codec
        // hardware to load. Currently @Ignore'd until CI emulator infra is
        // wired up. Source set named `androidDeviceTest` per AGP 9 KMP
        // convention (vs the older `androidInstrumentedTest`).
        getByName("androidDeviceTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.junit)
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

sqldelight {
    linkSqlite.set(false)
    databases {
        create("OdinDatabase") {
            packageName.set("id.homebase.api.sync.database")
            dialect(libs.sqldelight.sqlite338.dialect)
        }
    }
}