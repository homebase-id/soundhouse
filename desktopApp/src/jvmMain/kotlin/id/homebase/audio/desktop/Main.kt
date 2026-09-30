package id.homebase.audio.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import id.homebase.api.sync.database.DatabaseDriverFactory
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.audio.AudioApp
import id.homebase.audio.di.allAudioModules
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.app_name
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin

fun main() {
    // Must be set before any JNA user (FileKit) loads, or Windows can pick up a mismatched jnidispatch.
    System.setProperty("jna.nosys", "true")
    // jpackage launchers set this; `desktopApp:run` doesn't, so it keeps the Dev data directory.
    if (System.getProperty("jpackage.app-version") != null) {
        System.setProperty("app.rdns.name", "id.homebase.audio")
    }

    runBlocking { DatabaseManager.initializeWithRecovery(DatabaseDriverFactory()) }
    startKoin { modules(allAudioModules()) }
    FileKit.init(appId = "HomebaseSimpleAudio")

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
