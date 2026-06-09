package com.example.sharelink.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sharelink.adb.AdbTvClient
import com.example.sharelink.adb.AdbTvDiscoverer
import com.example.sharelink.data.TvConfig
import com.example.sharelink.data.TvSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Represents the current state of sending a link to the TV.
 */
sealed interface SendState {
    data object Idle : SendState
    data object Connecting : SendState
    data class Success(val message: String) : SendState
    data class Error(val message: String) : SendState
}

/**
 * Represents the connection test state.
 */
sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Connected(val message: String) : ConnectionTestState
    data class Failed(val message: String) : ConnectionTestState
}

data class TvListState(
    val tvs: List<TvConfig> = emptyList(),
    val selectedId: String = ""
) {
    val selectedTv: TvConfig? get() = tvs.firstOrNull { it.id == selectedId }
    val hasConfiguredTv: Boolean get() = selectedTv?.isConfigured == true
}

data class ShareLinkUiState(
    val sharedUrl: String? = null,
    val sendState: SendState = SendState.Idle,
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
    val sentHistory: List<SentLink> = emptyList(),
    val showAddTvForm: Boolean = false,
    val prefilledTvConfig: TvConfig? = null,
)

data class SentLink(
    val url: String,
    val tvName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true
)

class ShareLinkViewModel(
    private val tvSettingsRepository: TvSettingsRepository,
    private val adbClient: AdbTvClient,
    private val adbDiscoverer: AdbTvDiscoverer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareLinkUiState())
    val uiState: StateFlow<ShareLinkUiState> = _uiState.asStateFlow()

    val discoveredDevices: StateFlow<List<AdbTvDiscoverer.DiscoveredDevice>> = adbDiscoverer.discoveredDevices
    val isScanning: StateFlow<Boolean> = adbDiscoverer.isScanning

    init {
        // Disabled by default to optimize cold start and save resources. User can toggle scanning manually.
    }

    fun startScanning() {
        adbDiscoverer.startDiscovery()
    }

    fun stopScanning() {
        adbDiscoverer.stopDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }

    val tvListState: StateFlow<TvListState> = combine(
        tvSettingsRepository.tvList,
        tvSettingsRepository.selectedTvId
    ) { tvs, selectedId ->
        TvListState(tvs = tvs, selectedId = selectedId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TvListState())

    /**
     * Called when a share intent is received with a URL.
     * If a TV is configured, automatically sends the link.
     */
    fun onLinkReceived(url: String) {
        _uiState.update { it.copy(sharedUrl = url, sendState = SendState.Idle) }

        // Auto-send if a TV is selected
        val selected = tvListState.value.selectedTv
        if (selected != null && selected.isConfigured) {
            sendLinkToTv(url)
        }
    }

    /**
     * Sends the current or provided URL to the selected TV.
     */
    fun sendLinkToTv(url: String? = null) {
        val linkToSend = url ?: _uiState.value.sharedUrl ?: return
        val config = tvListState.value.selectedTv
        if (config == null || !config.isConfigured) {
            _uiState.update { it.copy(sendState = SendState.Error("No TV selected. Add a TV first.")) }
            return
        }

        _uiState.update { it.copy(sendState = SendState.Connecting) }

        viewModelScope.launch {
            val result = adbClient.openUrlOnTv(
                host = config.host,
                port = config.port,
                url = linkToSend
            )

            result.fold(
                onSuccess = {
                    val sentLink = SentLink(url = linkToSend, tvName = config.name, success = true)
                    _uiState.update {
                        it.copy(
                            sendState = SendState.Success("Link opened on ${config.name}!"),
                            sentHistory = listOf(sentLink) + it.sentHistory.take(9)
                        )
                    }
                },
                onFailure = { error ->
                    val sentLink = SentLink(url = linkToSend, tvName = config.name, success = false)
                    _uiState.update {
                        it.copy(
                            sendState = SendState.Error(
                                error.message ?: "Failed to send link to TV"
                            ),
                            sentHistory = listOf(sentLink) + it.sentHistory.take(9)
                        )
                    }
                }
            )
        }
    }

    /**
     * Tests the connection to the selected TV.
     */
    fun testConnection() {
        val config = tvListState.value.selectedTv
        if (config == null || !config.isConfigured) {
            _uiState.update {
                it.copy(connectionTestState = ConnectionTestState.Failed("No TV selected"))
            }
            return
        }

        _uiState.update { it.copy(connectionTestState = ConnectionTestState.Testing) }

        viewModelScope.launch {
            val result = adbClient.testConnection(host = config.host, port = config.port)

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(connectionTestState = ConnectionTestState.Connected("Connected to ${config.name}!"))
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            connectionTestState = ConnectionTestState.Failed(
                                error.message ?: "Connection failed"
                            )
                        )
                    }
                }
            )
        }
    }

    /** Adds a new TV or updates an existing one. */
    fun saveTv(config: TvConfig) {
        val tvToSave = if (config.id.isBlank()) {
            config.copy(id = UUID.randomUUID().toString())
        } else {
            config
        }
        viewModelScope.launch {
            tvSettingsRepository.saveTv(tvToSave)
            _uiState.update { it.copy(showAddTvForm = false, prefilledTvConfig = null) }
        }
    }

    /** Pre-fills and opens the Add TV form with discovered TV details. */
    fun prepareDiscoveredTvForSave(device: AdbTvDiscoverer.DiscoveredDevice) {
        val prefilled = TvConfig(
            name = device.name,
            host = device.host,
            port = device.port
        )
        _uiState.update { it.copy(showAddTvForm = true, prefilledTvConfig = prefilled) }
    }

    /** Removes a TV by ID. */
    fun removeTv(id: String) {
        viewModelScope.launch {
            tvSettingsRepository.removeTv(id)
        }
    }

    /** Selects a TV by ID. */
    fun selectTv(id: String) {
        viewModelScope.launch {
            tvSettingsRepository.selectTv(id)
            _uiState.update { it.copy(connectionTestState = ConnectionTestState.Idle) }
        }
    }

    /** Shows/hides the add TV form. */
    fun toggleAddTvForm(show: Boolean) {
        _uiState.update {
            it.copy(
                showAddTvForm = show,
                prefilledTvConfig = if (show) null else it.prefilledTvConfig
            )
        }
    }

    /** Resets the send state back to idle. */
    fun resetSendState() {
        _uiState.update { it.copy(sendState = SendState.Idle) }
    }

    /** Updates the shared URL (e.g., from manual input). */
    fun updateSharedUrl(url: String) {
        _uiState.update { it.copy(sharedUrl = url) }
    }
}
