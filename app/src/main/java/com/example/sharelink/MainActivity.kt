package com.example.sharelink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.sharelink.adb.AdbTvClient
import com.example.sharelink.adb.AdbTvDiscoverer
import com.example.sharelink.data.TvSettingsRepository
import com.example.sharelink.theme.ShareLinkTheme
import com.example.sharelink.ui.main.MainScreen
import com.example.sharelink.ui.main.ShareLinkViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: ShareLinkViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val factory = viewModelFactory {
            initializer {
                val app = applicationContext
                ShareLinkViewModel(
                    tvSettingsRepository = TvSettingsRepository(app),
                    adbClient = AdbTvClient(app.filesDir),
                    adbDiscoverer = AdbTvDiscoverer(app)
                )
            }
        }
        viewModel = ViewModelProvider(this, factory)[ShareLinkViewModel::class.java]

        // Handle the share intent that launched this activity
        handleIncomingIntent(intent)

        enableEdgeToEdge()
        setContent {
            ShareLinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Extracts the shared text/URL from an ACTION_SEND intent and passes
     * it to the ViewModel. If the TV is already configured, it will
     * automatically send the link.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                // Extract URL from shared text (some apps include extra text around the URL)
                val url = extractUrl(sharedText) ?: sharedText
                viewModel.onLinkReceived(url)
            }
        }
    }

    /**
     * Attempts to extract a URL from a string that may contain surrounding text.
     * For example: "Check this out: https://example.com via @someone"
     */
    private fun extractUrl(text: String): String? {
        val urlPattern = Regex("""https?://\S+""")
        return urlPattern.find(text)?.value
    }
}
