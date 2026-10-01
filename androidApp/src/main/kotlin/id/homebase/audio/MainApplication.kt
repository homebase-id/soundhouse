package id.homebase.audio

import android.app.Application
import id.homebase.api.ActivityProvider
import id.homebase.api.storage.SecureStorage
import id.homebase.api.storage.SharedPreferences
import id.homebase.api.sync.database.DatabaseDriverFactory
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.audio.di.allAudioModules
import id.homebase.audio.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        ActivityProvider.initializeApplicationContext(this)
        SecureStorage.initialize(this)
        SharedPreferences.initialize(this)
        // Koin's eager singletons read DatabaseManager.appDb, so the database opens first.
        runBlocking { DatabaseManager.initializeWithRecovery(DatabaseDriverFactory(applicationContext)) }
        val koin = startKoin {
            androidContext(this@MainApplication)
            modules(allAudioModules())
        }.koin
        // The service owns the foreground state; it is started whenever a track starts loading.
        appScope.launch {
            koin.get<PlaybackController>().state
                .map { it.isLoading }
                .distinctUntilChanged()
                .filter { it }
                .collect { PlaybackService.start(this@MainApplication) }
        }
    }
}
