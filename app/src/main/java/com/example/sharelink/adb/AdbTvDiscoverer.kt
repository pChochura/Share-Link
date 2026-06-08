package com.example.sharelink.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Handles mDNS discovery of ADB over TCP devices (Android TVs) on the local network.
 * Supports both _adb._tcp (native ADB discovery) and _googlecast._tcp (Chromecast/Google Cast discovery)
 * to ensure maximum compatibility and retrieve user-friendly TV names.
 */
@Suppress("DEPRECATION")
class AdbTvDiscoverer(context: Context) {

    data class DiscoveredDevice(
        val name: String,
        val host: String,
        val port: Int,
        val serviceName: String
    )

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var adbDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var castDiscoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    @Synchronized
    fun startDiscovery() {
        if (_isScanning.value) return

        _discoveredDevices.value = emptyList()

        // Acquire multicast lock to receive multicast/mDNS packets on Wi-Fi
        runCatching {
            multicastLock = wifiManager.createMulticastLock("AdbDiscoveryLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.e("AdbTvDiscoverer", "Failed to acquire multicast lock", it) }

        _isScanning.value = true

        // 1. Setup ADB Service discovery listener
        adbDiscoveryListener = createDiscoveryListener(
            serviceType = "_adb._tcp",
            defaultPort = null // Use resolved port
        )

        // 2. Setup Google Cast Service discovery listener
        castDiscoveryListener = createDiscoveryListener(
            serviceType = "_googlecast._tcp",
            defaultPort = AdbTvClient.DEFAULT_PORT // Override to standard ADB port 5555
        )

        try {
            nsdManager.discoverServices("_adb._tcp", NsdManager.PROTOCOL_DNS_SD, adbDiscoveryListener)
        } catch (e: Exception) {
            Log.e("AdbTvDiscoverer", "Failed to start ADB service discovery", e)
            adbDiscoveryListener = null
        }

        try {
            nsdManager.discoverServices("_googlecast._tcp", NsdManager.PROTOCOL_DNS_SD, castDiscoveryListener)
        } catch (e: Exception) {
            Log.e("AdbTvDiscoverer", "Failed to start Cast service discovery", e)
            castDiscoveryListener = null
        }

        if (adbDiscoveryListener == null && castDiscoveryListener == null) {
            _isScanning.value = false
            stopDiscovery()
        }
    }

    @Synchronized
    fun stopDiscovery() {
        adbDiscoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("AdbTvDiscoverer", "Failed to stop ADB service discovery", e)
            }
        }
        adbDiscoveryListener = null

        castDiscoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("AdbTvDiscoverer", "Failed to stop Cast service discovery", e)
            }
        }
        castDiscoveryListener = null

        multicastLock?.let {
            try {
                if (it.isHeld) it.release()
            } catch (e: Exception) {
                Log.e("AdbTvDiscoverer", "Failed to release multicast lock", e)
            }
        }
        multicastLock = null
        _isScanning.value = false
    }

    private fun createDiscoveryListener(
        serviceType: String,
        defaultPort: Int?
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                Log.e("AdbTvDiscoverer", "Discovery start failed for $type: $errorCode")
            }

            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                Log.e("AdbTvDiscoverer", "Discovery stop failed for $type: $errorCode")
            }

            override fun onDiscoveryStarted(type: String) {
                Log.d("AdbTvDiscoverer", "Discovery started for $type")
            }

            override fun onDiscoveryStopped(type: String) {
                Log.d("AdbTvDiscoverer", "Discovery stopped for $type")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("AdbTvDiscoverer", "Service found on $serviceType: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(resolvedInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e("AdbTvDiscoverer", "Resolve failed for $serviceType: $errorCode")
                    }

                    override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                        val hostAddress = resolvedServiceInfo.host.hostAddress ?: return
                        val name = resolvedServiceInfo.serviceName

                        // Filter out loopback
                        if (hostAddress.isBlank() || hostAddress == "127.0.0.1" || hostAddress == "::1") return

                        // Determine target port (use provided override or resolved port)
                        val port = defaultPort ?: resolvedServiceInfo.port

                        // Parse friendly name from TXT attributes if available (e.g. fn="Living Room TV")
                        val attributes = resolvedServiceInfo.attributes
                        val fnBytes = attributes["fn"]
                        val mdBytes = attributes["md"]
                        val friendlyName = when {
                            fnBytes != null -> String(fnBytes)
                            mdBytes != null -> String(mdBytes)
                            else -> cleanDeviceName(name)
                        }

                        // Update lists and merge duplicates by host/IP address
                        _discoveredDevices.update { list ->
                            val existingIndex = list.indexOfFirst { it.host == hostAddress }
                            if (existingIndex >= 0) {
                                val existing = list[existingIndex]
                                // Keep the friendlier name (without adb- serial prefix) if one is found
                                val bestName = if (existing.name.startsWith("adb-") && !friendlyName.startsWith("adb-")) {
                                    friendlyName
                                } else {
                                    existing.name
                                }
                                val updated = existing.copy(name = bestName, port = port, serviceName = name)
                                list.mapIndexed { idx, item -> if (idx == existingIndex) updated else item }
                            } else {
                                list + DiscoveredDevice(
                                    name = friendlyName,
                                    host = hostAddress,
                                    port = port,
                                    serviceName = name
                                )
                            }
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("AdbTvDiscoverer", "Service lost on $serviceType: ${serviceInfo.serviceName}")
                _discoveredDevices.update { list ->
                    list.filter { it.serviceName != serviceInfo.serviceName }
                }
            }
        }
    }

    private fun cleanDeviceName(rawName: String): String {
        return rawName.removePrefix("adb-")
    }
}
