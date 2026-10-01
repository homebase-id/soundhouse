package id.homebase.audio

import android.app.Application
import id.homebase.api.ActivityProvider
import id.homebase.api.storage.SecureStorage
import id.homebase.api.storage.SharedPreferences

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ActivityProvider.initializeApplicationContext(this)
        SecureStorage.initialize(this)
        SharedPreferences.initialize(this)
        AppStartup.begin(this)
    }
}
