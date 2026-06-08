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
 * Uses Android's native NsdManager and WifiManager.MulticastLock.
 */
@Suppress("DEPRECATION")
class AdbTvDiscoverer(context: Context) {

    data class DiscoveredDevice(
        val name: String,
        val host: String,
        val port: Int
    )

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    @Synchronized
    fun startDiscovery() {
        if (_isScanning.value) return

        _discoveredDevices.value = emptyList()

        // Acquire multicast lock to receive multicast packets on Wi-Fi
        runCatching {
            multicastLock = wifiManager.createMulticastLock("AdbDiscoveryLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.e("AdbTvDiscoverer", "Failed to acquire multicast lock", it) }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("AdbTvDiscoverer", "Discovery start failed: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("AdbTvDiscoverer", "Discovery stop failed: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("AdbTvDiscoverer", "Discovery started: $serviceType")
                _isScanning.value = true
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("AdbTvDiscoverer", "Discovery stopped: $serviceType")
                _isScanning.value = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("AdbTvDiscoverer", "Service found: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e("AdbTvDiscoverer", "Resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                        val hostAddress = resolvedServiceInfo.host.hostAddress ?: return
                        val port = resolvedServiceInfo.port
                        val name = resolvedServiceInfo.serviceName

                        // Filter out loopback
                        if (hostAddress.isBlank() || hostAddress == "127.0.0.1" || hostAddress == "::1") return

                        // Update list on UI thread or atomic updates
                        _discoveredDevices.update { list ->
                            val current = list.filter { it.host != hostAddress }
                            current + DiscoveredDevice(
                                name = cleanDeviceName(name),
                                host = hostAddress,
                                port = port
                            )
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("AdbTvDiscoverer", "Service lost: ${serviceInfo.serviceName}")
                _discoveredDevices.update { list ->
                    list.filter { it.name != cleanDeviceName(serviceInfo.serviceName) }
                }
            }
        }

        try {
            // Note: service type suffix '.' is optional but standard adb uses "_adb._tcp"
            nsdManager.discoverServices("_adb._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("AdbTvDiscoverer", "discoverServices failed", e)
            _isScanning.value = false
        }
    }

    @Synchronized
    fun stopDiscovery() {
        if (!_isScanning.value) return

        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("AdbTvDiscoverer", "stopServiceDiscovery failed", e)
            }
        }
        discoveryListener = null

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

    private fun cleanDeviceName(rawName: String): String {
        return rawName.removePrefix("adb-")
    }
}
