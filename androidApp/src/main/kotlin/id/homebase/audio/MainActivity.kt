package id.homebase.audio

import android.content.Intent
import id.homebase.audio.importing.TrackImporter
import id.homebase.audio.importing.isActive
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import id.homebase.api.ActivityProvider
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.core.config.AppConfig
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import io.github.vinceglb.filekit.manualFileKitCoreInitialization
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    private val youAuthFlowManager: YouAuthFlowManager by inject()
    private val importer: TrackImporter by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !AppStartup.ready.value }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ActivityProvider.initialize(this)
        FileKit.manualFileKitCoreInitialization(this)
        FileKit.init(this)
        handleIntent(intent)
        setContent {
            val ready by AppStartup.ready.collectAsStateWithLifecycle()
            // The splash covers this; composing AudioApp earlier would build services on the main thread.
            if (ready) AudioApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ActivityProvider.initialize(this)
        // Gives a sign-in callback that arrives with the resume a chance to land before
        // onAppResumed treats a closed browser as a cancelled login.
        lifecycleScope.launch {
            AppStartup.awaitReady()
            // Covers a queue that resumed while the app was in the background, when the service couldn't start.
            if (importer.jobs.value.any { it.isActive }) UploadService.start(this@MainActivity)
            delay(300)
            youAuthFlowManager.onAppResumed()
        }
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != AppConfig.DEEP_LINK_SCHEME) return
        val callbackUrl = data.toString()
        lifecycleScope.launch {
            AppStartup.awaitReady()
            youAuthFlowManager.handleCallback(callbackUrl)
        }
        intent.data = null
    }
}
