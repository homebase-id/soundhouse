package id.homebase.soundhouse

import android.app.Application
import id.homebase.api.sync.database.DatabaseDriverFactory
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.soundhouse.di.allAudioModules
import id.homebase.soundhouse.di.startAudioServices
import id.homebase.soundhouse.importing.TrackImporter
import id.homebase.soundhouse.importing.isActive
import id.homebase.soundhouse.playback.PlaybackController
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

// DB open and HTTP client are the slow, independent steps; failures crash rather than strand the splash.
object AppStartup {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    suspend fun awaitReady() {
        ready.first { it }
    }

    fun begin(app: Application) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val koin = startKoin {
                androidContext(app)
                modules(allAudioModules())
            }.koin
            coroutineScope {
                launch(Dispatchers.IO) { DatabaseManager.initializeWithRecovery(DatabaseDriverFactory(app)) }
                launch { koin.get<HttpClient>() }
            }
            koin.startAudioServices()
            _ready.value = true

            launch {
                koin.get<TrackImporter>().jobs
                    .map { jobs -> jobs.any { it.isActive } }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { launch(Dispatchers.Main) { UploadService.start(app) } }
            }

            // The service owns the foreground state; it is started whenever a track starts loading.
            koin.get<PlaybackController>().state
                .map { it.isLoading }
                .distinctUntilChanged()
                .filter { it }
                .collect { launch(Dispatchers.Main) { PlaybackService.start(app) } }
        }
    }
}
