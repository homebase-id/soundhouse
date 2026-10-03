package id.homebase.soundhouse.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import id.homebase.api.sync.database.DatabaseDriverFactory
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.soundhouse.AudioApp
import id.homebase.soundhouse.di.allAudioModules
import id.homebase.soundhouse.di.startAudioServices
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.app_name
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin

fun main() {
    // Must be set before any JNA user (FileKit) loads, or Windows can pick up a mismatched jnidispatch.
    System.setProperty("jna.nosys", "true")
    // jpackage launchers set this; `desktopApp:run` doesn't, so it keeps the Dev data directory.
    if (System.getProperty("jpackage.app-version") != null) {
        System.setProperty("app.rdns.name", "id.homebase.soundhouse")
    }

    runBlocking { DatabaseManager.initializeWithRecovery(DatabaseDriverFactory()) }
    startKoin { modules(allAudioModules()) }.koin.startAudioServices()
    FileKit.init(appId = "SimplyAudio")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(AR.string.app_name),
            state = rememberWindowState(width = 480.dp, height = 760.dp),
        ) {
            AudioApp()
        }
    }
}
