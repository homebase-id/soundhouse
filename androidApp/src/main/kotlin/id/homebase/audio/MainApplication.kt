package id.homebase.audio

import android.app.Application
import id.homebase.api.ActivityProvider
import id.homebase.api.storage.SecureStorage
import id.homebase.api.storage.SharedPreferences
import id.homebase.api.sync.database.DatabaseDriverFactory
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.audio.di.allAudioModules
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ActivityProvider.initializeApplicationContext(this)
        SecureStorage.initialize(this)
        SharedPreferences.initialize(this)
        // Koin's eager singletons read DatabaseManager.appDb, so the database opens first.
        runBlocking { DatabaseManager.initializeWithRecovery(DatabaseDriverFactory(applicationContext)) }
        startKoin {
            androidContext(this@MainApplication)
            modules(allAudioModules())
        }
    }
}
