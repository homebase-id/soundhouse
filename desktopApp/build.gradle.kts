import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

val versionProps = Properties()
versionProps.load(rootProject.file("gradle/version.properties").inputStream())
val versionName: String = versionProps.getProperty("version.name")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":audio-app"))
            implementation(compose.desktop.currentOs)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.resources)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.koin.core)
            implementation(libs.kermit)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.sqldelight.sqlite.driver.get().toString()) {
                exclude(group = "org.xerial", module = "sqlite-jdbc")
            }
            implementation(libs.sqlite.jdbc.crypt)
        }
    }
}

compose.desktop {
    application {
        mainClass = "id.homebase.audio.desktop.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.appearance=system")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            packageName = "SimplyAudio"
            packageVersion = versionName
            description = "Simply Audio"
            vendor = "Homebase"

            modules(
                "java.base",
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.net.http",
                "java.prefs",
                "java.sql",
                "java.desktop",
                "jdk.httpserver",
                "jdk.unsupported",
                "jdk.crypto.ec",
                "jdk.security.auth",
            )

            macOS {
                iconFile.set(project.rootProject.file("icons/icon.icns"))
                packageName = "Simply Audio"
                bundleID = "id.homebase.audio"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Allow access to the microphone to record audio for your library.</string>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.rootProject.file("icons/icon.ico"))
                menuGroup = "Homebase"
                upgradeUuid = "5d0f8f0e-2a0b-4bb2-9d4e-2f0c3a9a7d41"
                perUserInstall = true
            }
            linux {
                iconFile.set(project.rootProject.file("icons/icon.png"))
            }
        }
    }
}
