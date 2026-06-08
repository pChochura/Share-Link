package com.example.sharelink

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.example.sharelink.adb.AdbTvClient
import com.example.sharelink.data.TvConfig
import com.example.sharelink.data.TvSettingsRepository
import com.example.sharelink.theme.CardSurface
import com.example.sharelink.theme.CyanPrimary
import com.example.sharelink.theme.DeepNavy
import com.example.sharelink.theme.ElevatedSurface
import com.example.sharelink.theme.ErrorRed
import com.example.sharelink.theme.ShareLinkTheme
import com.example.sharelink.theme.SuccessGreen
import com.example.sharelink.theme.TextPrimary
import com.example.sharelink.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ShareState {
    object Checking : ShareState
    data class Sending(val tvName: String) : ShareState
    data class SelectTv(val tvs: List<TvConfig>, val url: String) : ShareState
    data class Success(val tvName: String) : ShareState
    data class Error(val tvName: String, val message: String) : ShareState
}

class ShareActivity : ComponentActivity() {

    private lateinit var tvSettingsRepository: TvSettingsRepository
    private lateinit var adbClient: AdbTvClient
    private var sendJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tvSettingsRepository = TvSettingsRepository(applicationContext)
        adbClient = AdbTvClient(applicationContext.filesDir)

        var shareState by mutableStateOf<ShareState>(ShareState.Checking)
        var extractedUrl by mutableStateOf("")

        setContent {
            ShareLinkTheme {
                ShareFlowUi(
                    state = shareState,
                    url = extractedUrl,
                    onSelectTv = { tv -> sendLinkToTv(tv, extractedUrl) { shareState = it } },
                    onCancel = {
                        sendJob?.cancel()
                        finish()
                    }
                )
            }
        }

        handleShareIntent { state, url ->
            shareState = state
            extractedUrl = url
        }
    }

    private fun handleShareIntent(onStateUpdate: (ShareState, String) -> Unit) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val url = extractUrl(sharedText) ?: sharedText
                
                lifecycleScope.launch {
                    val tvs = tvSettingsRepository.tvList.first()
                    when {
                        tvs.isEmpty() -> {
                            Toast.makeText(this@ShareActivity, "No TVs configured. Open app to add one.", Toast.LENGTH_LONG).show()
                            val mainIntent = Intent(this@ShareActivity, MainActivity::class.java).apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, sharedText)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            startActivity(mainIntent)
                            finish()
                        }
                        tvs.size == 1 -> {
                            val tv = tvs.first()
                            sendLinkToTv(tv, url) { state -> onStateUpdate(state, url) }
                        }
                        else -> {
                            onStateUpdate(ShareState.SelectTv(tvs, url), url)
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Empty shared text", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun sendLinkToTv(tv: TvConfig, url: String, onStateUpdate: (ShareState) -> Unit) {
        sendJob?.cancel()
        sendJob = lifecycleScope.launch {
            onStateUpdate(ShareState.Sending(tv.name))
            val result = adbClient.openUrlOnTv(tv.host, tv.port, url)
            if (result.isSuccess) {
                onStateUpdate(ShareState.Success(tv.name))
                delay(1500) // Show success checkmark for 1.5 seconds
                finish()
            } else {
                onStateUpdate(ShareState.Error(tv.name, result.exceptionOrNull()?.message ?: "Unknown error"))
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val urlPattern = Regex("""https?://\S+""")
        return urlPattern.find(text)?.value
    }
}

@Composable
fun ShareFlowUi(
    state: ShareState,
    url: String,
    onSelectTv: (TvConfig) -> Unit,
    onCancel: () -> Unit
) {
    when (state) {
        is ShareState.Checking -> {
            Box(Modifier.fillMaxSize())
        }
        is ShareState.Sending -> {
            ActionDialog(
                title = "Sending Link",
                content = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(color = CyanPrimary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Connecting to ${state.tvName}…",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                },
                onDismiss = onCancel
            )
        }
        is ShareState.Success -> {
            ActionDialog(
                title = "Link Sent!",
                content = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = SuccessGreen.copy(alpha = 0.15f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✓", color = SuccessGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Opened successfully on ${state.tvName}",
                            color = SuccessGreen,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                onDismiss = onCancel
            )
        }
        is ShareState.Error -> {
            ActionDialog(
                title = "Failed to Send",
                content = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = ErrorRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✕", color = ErrorRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Error on ${state.tvName}:",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.message,
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close", color = TextPrimary)
                        }
                    }
                },
                onDismiss = onCancel
            )
        }
        is ShareState.SelectTv -> {
            ActionDialog(
                title = "Send to TV",
                content = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = url,
                            color = CyanPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSurface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Select a target device:",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            items(state.tvs) { tv ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ElevatedSurface, RoundedCornerShape(12.dp))
                                        .clickable { onSelectTv(tv) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = CyanPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = tv.name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = tv.host,
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onDismiss = onCancel
            )
        }
    }
}

@Composable
fun ActionDialog(
    title: String,
    content: @Composable () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = DeepNavy,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}
