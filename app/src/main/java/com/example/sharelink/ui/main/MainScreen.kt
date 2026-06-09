package com.example.sharelink.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sharelink.data.TvConfig
import com.example.sharelink.theme.AmberAccent
import com.example.sharelink.theme.CyanDark
import com.example.sharelink.theme.CyanPrimary
import com.example.sharelink.theme.ErrorRed
import com.example.sharelink.theme.PurpleAccent
import com.example.sharelink.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.sharelink.adb.AdbTvDiscoverer
import androidx.compose.foundation.layout.PaddingValues
import com.example.sharelink.theme.ElevatedSurface
import com.example.sharelink.theme.TextPrimary
import com.example.sharelink.theme.TextSecondary
import com.example.sharelink.theme.CardSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ShareLinkViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tvListState by viewModel.tvListState.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Cast,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Share to TV",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Shared Link Card
            SharedLinkCard(
                sharedUrl = uiState.sharedUrl,
                sendState = uiState.sendState,
                selectedTv = tvListState.selectedTv,
                onSend = { viewModel.sendLinkToTv() },
                onUrlChange = { viewModel.updateSharedUrl(it) },
                onDismissStatus = { viewModel.resetSendState() },
            )

            // TV List Card
            TvListCard(
                tvListState = tvListState,
                connectionTestState = uiState.connectionTestState,
                showAddForm = uiState.showAddTvForm,
                prefilledTvConfig = uiState.prefilledTvConfig,
                onSelect = { viewModel.selectTv(it) },
                onSave = { viewModel.saveTv(it) },
                onRemove = { viewModel.removeTv(it) },
                onTestConnection = { viewModel.testConnection() },
                onToggleAddForm = { viewModel.toggleAddTvForm(it) },
            )

            // Discovered TVs Card
            DiscoveredTvsCard(
                discoveredDevices = discoveredDevices,
                isScanning = isScanning,
                onAddDiscoveredTv = { viewModel.prepareDiscoveredTvForSave(it) },
                onToggleScanning = {
                    if (isScanning) {
                        viewModel.stopScanning()
                    } else {
                        viewModel.startScanning()
                    }
                }
            )

            // History Card
            if (uiState.sentHistory.isNotEmpty()) {
                HistoryCard(history = uiState.sentHistory)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Shared Link Card ─────────────────────────────────────────────────

@Composable
private fun SharedLinkCard(
    sharedUrl: String?,
    sendState: SendState,
    selectedTv: TvConfig?,
    onSend: () -> Unit,
    onUrlChange: (String) -> Unit,
    onDismissStatus: () -> Unit,
) {
    val hasTv = selectedTv?.isConfigured == true

    GlowCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(icon = Icons.Filled.Link, title = "Shared Link")

            if (sharedUrl != null) {
                // Display the received URL
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = sharedUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyanPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Send button with target TV name
                Button(
                    onClick = onSend,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = sendState !is SendState.Connecting && hasTv,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    when (sendState) {
                        is SendState.Connecting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sending…")
                        }
                        else -> {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (hasTv) "Send to ${selectedTv.name}" else "Send to TV",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                if (!hasTv) {
                    Text(
                        "📺 Add a TV below to send links",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberAccent,
                    )
                }
            } else {
                // No link shared yet — show input field
                var manualUrl by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text("Enter URL or share a link to this app") },
                    placeholder = { Text("https://example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        cursorColor = CyanPrimary,
                        focusedLabelColor = CyanPrimary,
                    ),
                )

                Button(
                    onClick = {
                        if (manualUrl.isNotBlank()) {
                            onUrlChange(manualUrl)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = manualUrl.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (hasTv) "Send to ${selectedTv.name}" else "Send to TV",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Status message
            AnimatedVisibility(
                visible = sendState !is SendState.Idle && sendState !is SendState.Connecting,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                StatusBanner(sendState = sendState, onDismiss = onDismissStatus)
            }
        }
    }
}

// ── TV List Card ─────────────────────────────────────────────────────

@Composable
private fun TvListCard(
    tvListState: TvListState,
    connectionTestState: ConnectionTestState,
    showAddForm: Boolean,
    prefilledTvConfig: TvConfig?,
    onSelect: (String) -> Unit,
    onSave: (TvConfig) -> Unit,
    onRemove: (String) -> Unit,
    onTestConnection: () -> Unit,
    onToggleAddForm: (Boolean) -> Unit,
) {
    GlowCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(icon = Icons.Filled.Tv, title = "TVs")
                if (tvListState.tvs.isNotEmpty()) {
                    StatusChip(
                        text = "${tvListState.tvs.size} saved",
                        icon = Icons.Filled.Tv,
                        color = CyanPrimary,
                    )
                }
            }

            // TV List
            if (tvListState.tvs.isNotEmpty()) {
                tvListState.tvs.forEach { tv ->
                    val isSelected = tv.id == tvListState.selectedId
                    TvListItem(
                        tv = tv,
                        isSelected = isSelected,
                        onSelect = { onSelect(tv.id) },
                        onRemove = { onRemove(tv.id) },
                    )
                }

                // Test connection for selected TV
                val selectedTv = tvListState.selectedTv
                if (selectedTv != null) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = connectionTestState !is ConnectionTestState.Testing,
                    ) {
                        when (connectionTestState) {
                            is ConnectionTestState.Testing -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Testing ${selectedTv.name}…")
                            }
                            else -> {
                                Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Test ${selectedTv.name}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Connection test result
                    AnimatedVisibility(
                        visible = connectionTestState !is ConnectionTestState.Idle && connectionTestState !is ConnectionTestState.Testing,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        when (connectionTestState) {
                            is ConnectionTestState.Connected -> {
                                StatusBannerSimple(
                                    message = connectionTestState.message,
                                    icon = Icons.Filled.CheckCircle,
                                    color = SuccessGreen,
                                )
                            }
                            is ConnectionTestState.Failed -> {
                                StatusBannerSimple(
                                    message = connectionTestState.message,
                                    icon = Icons.Filled.Error,
                                    color = ErrorRed,
                                )
                            }
                            else -> {}
                        }
                    }
                }
            } else if (!showAddForm) {
                // Empty state
                Text(
                    "No TVs added yet. Add your first TV to get started.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Add TV button / form
            AnimatedVisibility(
                visible = showAddForm,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                AddTvForm(
                    initialTvConfig = prefilledTvConfig ?: TvConfig(),
                    onSave = onSave,
                    onCancel = { onToggleAddForm(false) },
                )
            }

            if (!showAddForm) {
                FilledTonalButton(
                    onClick = { onToggleAddForm(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add TV", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── TV List Item ─────────────────────────────────────────────────────

@Composable
private fun TvListItem(
    tv: TvConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val borderColor = if (isSelected) CyanPrimary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (isSelected) CyanPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tv.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${tv.host}:${tv.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove",
                tint = ErrorRed.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Add TV Form ──────────────────────────────────────────────────────

@Composable
private fun AddTvForm(
    initialTvConfig: TvConfig = TvConfig(),
    onSave: (TvConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var nameInput by remember(initialTvConfig) { mutableStateOf(initialTvConfig.name) }
    var hostInput by remember(initialTvConfig) { mutableStateOf(initialTvConfig.host) }
    var portInput by remember(initialTvConfig) { mutableStateOf(initialTvConfig.port.toString()) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "New TV",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PurpleAccent,
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("TV Name") },
                placeholder = { Text("Living Room TV") },
                leadingIcon = { Icon(Icons.Filled.Tv, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    cursorColor = CyanPrimary,
                    focusedLabelColor = CyanPrimary,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    leadingIcon = { Icon(Icons.Filled.Router, contentDescription = null) },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        cursorColor = CyanPrimary,
                        focusedLabelColor = CyanPrimary,
                    ),
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        cursorColor = CyanPrimary,
                        focusedLabelColor = CyanPrimary,
                    ),
                )
            }

            Button(
                onClick = {
                    val port = portInput.toIntOrNull() ?: 5555
                    val name = nameInput.ifBlank { hostInput }
                    onSave(TvConfig(host = hostInput, port = port, name = name))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hostInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save TV", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── History Card ─────────────────────────────────────────────────────

@Composable
private fun HistoryCard(history: List<SentLink>) {
    GlowCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(icon = Icons.Filled.History, title = "Recent")

            history.forEach { link ->
                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (link.success) SuccessGreen else ErrorRed),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = link.url,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (link.tvName.isNotBlank()) {
                            Text(
                                text = "→ ${link.tvName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeFormat.format(Date(link.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Shared Components ────────────────────────────────────────────────

@Composable
private fun GlowCard(
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CyanPrimary.copy(alpha = 0.15f),
                        CyanDark.copy(alpha = 0.05f),
                    ),
                ),
                shape = RoundedCornerShape(16.dp),
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = CyanPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusChip(text: String, icon: ImageVector, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusBanner(sendState: SendState, onDismiss: () -> Unit) {
    val (message, icon, color) = when (sendState) {
        is SendState.Success -> Triple(sendState.message, Icons.Filled.CheckCircle, SuccessGreen)
        is SendState.Error -> Triple(sendState.message, Icons.Filled.Error, ErrorRed)
        else -> return
    }

    StatusBannerSimple(message = message, icon = icon, color = color)
}

@Composable
private fun StatusBannerSimple(
    message: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300),
        label = "statusAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DiscoveredTvsCard(
    discoveredDevices: List<AdbTvDiscoverer.DiscoveredDevice>,
    isScanning: Boolean,
    onAddDiscoveredTv: (AdbTvDiscoverer.DiscoveredDevice) -> Unit,
    onToggleScanning: () -> Unit,
) {
    GlowCard {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(icon = Icons.Filled.Wifi, title = "Discovered on Wi-Fi")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = CyanPrimary,
                        )
                        IconButton(
                            onClick = onToggleScanning,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop Scanning",
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onToggleScanning,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Start Scanning",
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (discoveredDevices.isEmpty()) {
                Text(
                    text = if (isScanning) "Searching local network for ADB-enabled TVs…" else "Scan stopped",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            } else {
                discoveredDevices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ElevatedSurface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Filled.Tv,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = device.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${device.host}:${device.port}",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        Button(
                            onClick = { onAddDiscoveredTv(device) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

