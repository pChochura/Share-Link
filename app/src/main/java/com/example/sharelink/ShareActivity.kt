package com.example.sharelink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ShareState {
    object Checking : ShareState
    
    // URL Sharing states
    data class Sending(val tvName: String) : ShareState
    data class SelectTv(val tvs: List<TvConfig>, val url: String) : ShareState
    data class Success(val tvName: String) : ShareState
    data class Error(val tv: TvConfig, val url: String, val message: String) : ShareState
    
    // File Sharing states
    data class SendingFile(val tvName: String, val fileName: String, val progressMsg: String, val progress: Float = -1f) : ShareState
    data class SelectTvForFile(val tvs: List<TvConfig>, val fileName: String, val fileSizeStr: String) : ShareState
    data class FileSuccess(val tvName: String, val fileName: String) : ShareState
    data class FileError(val tv: TvConfig, val uri: Uri, val fileName: String, val fileSizeStr: String, val message: String) : ShareState
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
                    onSelectTv = { tv ->
                        val currentState = shareState
                        when (currentState) {
                            is ShareState.SelectTvForFile -> {
                                val streamUri = getSharedStreamUri()
                                if (streamUri != null) {
                                    sendFileToTv(tv, streamUri, currentState.fileName, currentState.fileSizeStr) { shareState = it }
                                } else {
                                    shareState = ShareState.FileError(tv, Uri.EMPTY, currentState.fileName, "0 B", "Could not resolve shared file stream.")
                                }
                            }
                            is ShareState.FileError -> {
                                sendFileToTv(tv, currentState.uri, currentState.fileName, currentState.fileSizeStr) { shareState = it }
                            }
                            is ShareState.Error -> {
                                sendLinkToTv(tv, currentState.url) { shareState = it }
                            }
                            else -> {
                                sendLinkToTv(tv, extractedUrl) { shareState = it }
                            }
                        }
                    },
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

    private fun getSharedStreamUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun handleShareIntent(onStateUpdate: (ShareState, String) -> Unit) {
        val streamUri = getSharedStreamUri()

        if (streamUri != null) {
            // Handing file sharing
            val (fileName, fileSize) = getFileInfo(streamUri)
            val fileSizeStr = formatFileSize(fileSize)

            lifecycleScope.launch {
                val tvs = tvSettingsRepository.tvList.first()
                when {
                    tvs.isEmpty() -> {
                        Toast.makeText(this@ShareActivity, "No TVs configured. Open app to add one.", Toast.LENGTH_LONG).show()
                        val mainIntent = Intent(this@ShareActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                    tvs.size == 1 -> {
                        val tv = tvs.first()
                        sendFileToTv(tv, streamUri, fileName, fileSizeStr) { state -> onStateUpdate(state, "") }
                    }
                    else -> {
                        onStateUpdate(ShareState.SelectTvForFile(tvs, fileName, fileSizeStr), "")
                    }
                }
            }
        } else if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            // Handling text/URL sharing
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
                delay(1500)
                finish()
            } else {
                onStateUpdate(ShareState.Error(tv, url, result.exceptionOrNull()?.message ?: "Unknown error"))
            }
        }
    }

    private fun sendFileToTv(
        tv: TvConfig,
        uri: Uri,
        fileName: String,
        fileSizeStr: String,
        onStateUpdate: (ShareState) -> Unit
    ) {
        sendJob?.cancel()
        sendJob = lifecycleScope.launch {
            onStateUpdate(ShareState.SendingFile(tv.name, fileName, "Preparing file…"))

            val cacheFile = withContext(Dispatchers.IO) {
                copyUriToCache(uri, fileName)
            }

            if (cacheFile == null || !cacheFile.exists()) {
                onStateUpdate(ShareState.FileError(tv, uri, fileName, fileSizeStr, "Failed to read shared file from phone memory."))
                return@launch
            }

            onStateUpdate(ShareState.SendingFile(tv.name, fileName, "Pushing to TV ($fileSizeStr)…", -1f))

            val mimeType = intent.type
            val result = adbClient.pushFileAndOpen(
                host = tv.host,
                port = tv.port,
                localFile = cacheFile,
                remoteFileName = fileName,
                mimeType = mimeType,
                onProgress = { progress ->
                    val pct = (progress * 100).toInt()
                    val isApk = fileName.endsWith(".apk", ignoreCase = true) || mimeType == "application/vnd.android.package-archive"
                    val actionName = if (isApk) "Installing on TV" else "Pushing to TV"
                    onStateUpdate(ShareState.SendingFile(tv.name, fileName, "$actionName: $pct% ($fileSizeStr)", progress))
                }
            )

            withContext(Dispatchers.IO) {
                cacheFile.delete()
            }

            if (result.isSuccess) {
                onStateUpdate(ShareState.FileSuccess(tv.name, fileName))
                delay(2000)
                finish()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                onStateUpdate(ShareState.FileError(tv, uri, fileName, fileSizeStr, errorMsg))
            }
        }
    }

    private fun getFileInfo(uri: Uri): Pair<String, Long> {
        var name = "shared_file"
        var size = -1L
        try {
            if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                name = file.name
                size = file.length()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(name, size)
    }

    private fun copyUriToCache(uri: Uri, fileName: String): File? {
        return try {
            val cacheFile = File(cacheDir, fileName)
            cacheFile.delete()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                cacheFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "Unknown size"
        val kb = size / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } else {
            String.format(java.util.Locale.US, "%.1f KB", kb)
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
                            text = "Error on ${state.tv.name}:",
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onCancel,
                                colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Close", color = TextPrimary)
                            }
                            Button(
                                onClick = { onSelectTv(state.tv) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry", color = DeepNavy, fontWeight = FontWeight.Bold)
                            }
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
        is ShareState.SendingFile -> {
            ActionDialog(
                title = "Sending File",
                content = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        if (state.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                color = CyanPrimary,
                                trackColor = ElevatedSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            val pct = (state.progress * 100).toInt()
                            Text(
                                text = "$pct%",
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            CircularProgressIndicator(color = CyanPrimary)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.fileName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.progressMsg,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                },
                onDismiss = onCancel
            )
        }
        is ShareState.FileSuccess -> {
            ActionDialog(
                title = "File Pushed!",
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
                        val isApk = state.fileName.endsWith(".apk", ignoreCase = true)
                        Text(
                            text = if (isApk) "Installed successfully on ${state.tvName}" else "Sent & opened on ${state.tvName}",
                            color = SuccessGreen,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.fileName,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                onDismiss = onCancel
            )
        }
        is ShareState.FileError -> {
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
                            text = "Error sending ${state.fileName}:",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onCancel,
                                colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Close", color = TextPrimary)
                            }
                            Button(
                                onClick = { onSelectTv(state.tv) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry", color = DeepNavy, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                onDismiss = onCancel
            )
        }
        is ShareState.SelectTvForFile -> {
            ActionDialog(
                title = "Send File to TV",
                content = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSurface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CyanPrimary.copy(alpha = 0.1f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = CyanPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.fileName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = state.fileSizeStr,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
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
