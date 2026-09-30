package id.homebase.audio

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ActivityProvider.initialize(this)
        FileKit.manualFileKitCoreInitialization(this)
        FileKit.init(this)
        handleIntent(intent)
        setContent { AudioApp() }
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
            delay(300)
            youAuthFlowManager.onAppResumed()
        }
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != AppConfig.DEEP_LINK_SCHEME) return
        val callbackUrl = data.toString()
        lifecycleScope.launch { youAuthFlowManager.handleCallback(callbackUrl) }
        intent.data = null
    }
}
