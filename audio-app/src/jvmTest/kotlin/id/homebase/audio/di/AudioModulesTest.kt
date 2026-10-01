package id.homebase.audio.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import coil3.ImageLoader
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.audio.data.TrackManager
import id.homebase.audio.download.DownloadStore
import id.homebase.audio.importing.TrackImporter
import id.homebase.audio.playback.PlaybackController
import id.homebase.audio.playback.TrackLocator
import id.homebase.audio.ui.library.LibraryViewModel
import id.homebase.audio.ui.loading.AppLoadingViewModel
import id.homebase.audio.ui.player.PlayerViewModel
import id.homebase.audio.ui.record.RecordViewModel
import id.homebase.auth.login.LoginViewModel
import id.homebase.core.auth.AuthConnectionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Builds the real Koin graph and resolves everything the screens need, so a missing or mistyped
 * binding fails here instead of on the first tap after sign-in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioModulesTest {
    private val realHome = System.getProperty("user.home")
    private val home = Files.createTempDirectory("hba-home").toFile()

    @BeforeTest
    fun setUp() {
        // Keeps restored sessions, the database and downloads away from the real app data dir.
        System.setProperty("user.home", home.absolutePath)
        Dispatchers.setMain(Dispatchers.Default)
        runBlocking {
            runCatching { DatabaseManager.initialize { JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) } }
        }
    }

    // Main is left set: the resolved view models keep collecting on it and Koin can't clear them,
    // so resetMain would race those collectors. Later tests set their own Main.
    @AfterTest
    fun tearDown() {
        stopKoin()
        System.setProperty("user.home", realHome)
        home.deleteRecursively()
    }

    @Test
    fun `every screen and service resolves`() {
        val koin = startKoin { modules(allAudioModules()) }.koin
        assertNotNull(koin.get<AuthConnectionCoordinator>())
        // Injected straight from composables (PublicAvatar on the login screen), not via a view model.
        assertNotNull(koin.get<ImageLoader>())
        assertNotNull(koin.get<PlaybackController>())
        assertNotNull(koin.get<TrackLocator>())
        assertNotNull(koin.get<DownloadStore>())
        assertNotNull(koin.get<TrackImporter>())
        assertNotNull(koin.get<TrackManager>())
        assertNotNull(koin.get<AppLoadingViewModel>())
        assertNotNull(koin.get<LoginViewModel>())
        assertNotNull(koin.get<LibraryViewModel>())
        assertNotNull(koin.get<PlayerViewModel>())
        assertNotNull(koin.get<RecordViewModel>())
    }
}
