package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.WifiBand
import com.example.model.WifiNetwork
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class WifiScannerService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val scanDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val networks: StateFlow<List<WifiNetwork>> = _networks.asStateFlow()

    private val _connectedNetwork = MutableStateFlow<WifiNetwork?>(null)
    val connectedNetwork: StateFlow<WifiNetwork?> = _connectedNetwork.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isSimulatedMode = MutableStateFlow(false)
    val isSimulatedMode: StateFlow<Boolean> = _isSimulatedMode.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val publicationLock = Any()
    @Volatile private var disposed = false
    private var pollingJob: Job? = null
    private var pendingSimulationJob: Job? = null
    private var scanReceiverRegistered = false

    // Initialized BEFORE init starts its Dispatchers.Default polling coroutine.
    // Kotlin initializes class fields in declaration order. Keeping this below
    // init caused a background startup NPE from a not-yet-initialized collection.
    private val baseSimulatedNetworks: List<WifiNetwork> = createApartmentBuildingNeighbors()

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                refreshRealScanResults()
            }
        }
    }

    init {
        checkPermissions()
        // Start simulated mode by default if no hardware results or permissions
        if (!hasHardwareWifi() || !checkLocationPermissionGranted()) {
            _isSimulatedMode.value = true
        }
        startScanLoop()
    }

    fun setSimulatedMode(enabled: Boolean) {
        synchronized(publicationLock) {
            if (disposed) return
            pendingSimulationJob?.cancel()
            // Always clear readings when changing source. Never carry DEMO into LIVE.
            _networks.value = emptyList()
            _connectedNetwork.value = null
            _isSimulatedMode.value = enabled
        }
        triggerScan()
    }

    /** An atomic real-radio snapshot; simulated connected data can never be recorded. */
    fun realConnectedSnapshotOrNull(): WifiNetwork? = synchronized(publicationLock) {
        if (disposed || _isSimulatedMode.value || !_hasLocationPermission.value) null
        else _connectedNetwork.value?.takeIf {
            it.isConnected && it.ssid.isNotBlank() && it.rssi in -100..-1 && it.frequency > 0
        }
    }

    fun hasHardwareWifi(): Boolean {
        return wifiManager != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
    }

    fun checkPermissions() {
        _hasLocationPermission.value = checkLocationPermissionGranted()
    }

    private fun checkLocationPermissionGranted(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation
    }

    fun registerReceiver() {
        synchronized(publicationLock) {
            if (disposed || scanReceiverRegistered || wifiManager == null) return
            try {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                context.registerReceiver(scanReceiver, filter)
                scanReceiverRegistered = true
            } catch (_: Exception) {
                // Registration may fail during permission/lifecycle transitions.
            }
        }
    }

    fun unregisterReceiver() {
        synchronized(publicationLock) {
            if (scanReceiverRegistered) {
                try {
                    context.unregisterReceiver(scanReceiver)
                } catch (_: Exception) {
                    // Receiver may already be gone.
                }
                scanReceiverRegistered = false
            }
        }
    }

    fun triggerScan() {
        synchronized(publicationLock) {
            if (disposed) return
            _isScanning.value = true
        }
        checkPermissions()

        if (_isSimulatedMode.value) {
            synchronized(publicationLock) {
                if (disposed) return
                pendingSimulationJob?.cancel()
                pendingSimulationJob = scope.launch(scanDispatcher) {
                    try {
                        delay(300)
                        updateSimulatedNetworks()
                    } finally {
                        if (!disposed) _isScanning.value = false
                    }
                }
            }
            return
        }

        if (wifiManager == null || !_hasLocationPermission.value) {
            synchronized(publicationLock) {
                if (disposed) return
                _networks.value = emptyList()
                _connectedNetwork.value = null
                _isSimulatedMode.value = true
            }
            updateSimulatedNetworks()
            _isScanning.value = false
            return
        }

        try {
            registerReceiver()
            @Suppress("DEPRECATION")
            val scanStarted = wifiManager.startScan()
            if (!scanStarted) {
                // Throttled by OS: fetch last known scan results immediately
                refreshRealScanResults()
            }
        } catch (e: Exception) {
            refreshRealScanResults()
        } finally {
            _isScanning.value = false
        }
    }

    private fun startScanLoop() {
        pollingJob?.cancel()
        pollingJob = scope.launch(scanDispatcher) {
            while (isActive && !disposed) {
                if (_isSimulatedMode.value) {
                    updateSimulatedNetworks()
                } else {
                    refreshRealScanResults()
                }
                delay(2500) // update every 2.5 seconds
            }
        }
    }

    private fun refreshRealScanResults() {
        if (disposed || _isSimulatedMode.value || wifiManager == null) return

        try {
            val results: List<ScanResult> = wifiManager.scanResults ?: emptyList()
            val connectionInfo = wifiManager.connectionInfo
            val currentSsid = sanitizeSsid(connectionInfo?.ssid)
            val currentBssid = connectionInfo?.bssid

            // An empty scan does not imply a missing real connection.
            // Continue to WiFiInfo below; never substitute simulated neighbors.

            val mapped = results.map { scan ->
                val isConn = (currentBssid != null && scan.BSSID.equals(currentBssid, ignoreCase = true)) ||
                        (currentSsid.isNotEmpty() && scan.SSID.equals(currentSsid, ignoreCase = true))
                val channelWidth = parseChannelWidth(scan)

                WifiNetwork(
                    ssid = if (scan.SSID.isNullOrEmpty()) "<Hidden Network>" else scan.SSID,
                    bssid = scan.BSSID ?: "00:00:00:00:00:00",
                    rssi = if (isConn && connectionInfo?.rssi in -100..-1) connectionInfo.rssi else scan.level,
                    frequency = scan.frequency,
                    channelWidthMhz = channelWidth,
                    capabilities = scan.capabilities ?: "",
                    isConnected = isConn,
                    linkSpeedMbps = if (isConn) connectionInfo?.linkSpeed else null,
                    ipAddress = if (isConn) formatIp(connectionInfo?.ipAddress) else null
                )
            }.sortedByDescending { it.rssi }

            val connected = mapped.find { it.isConnected } ?: if (
                currentSsid.isNotEmpty() && currentSsid != "<unknown ssid>" &&
                connectionInfo != null && connectionInfo.rssi in -100..-1 && connectionInfo.frequency > 0
            ) {
                WifiNetwork(
                    ssid = currentSsid,
                    bssid = currentBssid ?: "02:00:00:00:00:00",
                    rssi = connectionInfo.rssi,
                    frequency = connectionInfo.frequency,
                    channelWidthMhz = 20,
                    capabilities = "",
                    isConnected = true,
                    linkSpeedMbps = connectionInfo.linkSpeed,
                    ipAddress = formatIp(connectionInfo?.ipAddress)
                )
            } else null

            // Reject stale results delivered after a mode switch or teardown.
            synchronized(publicationLock) {
                if (disposed || _isSimulatedMode.value) return
                _networks.value = mapped
                _connectedNetwork.value = connected
            }
        } catch (e: SecurityException) {
            synchronized(publicationLock) {
                if (disposed) return
                _hasLocationPermission.value = false
                _networks.value = emptyList()
                _connectedNetwork.value = null
                _isSimulatedMode.value = true
            }
            updateSimulatedNetworks()
        } catch (e: Exception) {
            // Error fetching scan results
        }
    }

    private fun parseChannelWidth(scan: ScanResult): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (scan.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                else -> 20
            }
        } else {
            20
        }
    }

    private fun sanitizeSsid(rawSsid: String?): String {
        if (rawSsid == null) return ""
        return rawSsid.removePrefix("\"").removeSuffix("\"")
    }

    private fun formatIp(ip: Int?): String? {
        if (ip == null || ip == 0) return null
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }

    // Apartment simulated Wi-Fi environment
    private fun updateSimulatedNetworks() {
        if (disposed || !_isSimulatedMode.value) return
        // Apply realistic small RF fluctuations (+/- 1-2 dBm)
        val updated = baseSimulatedNetworks.map { net ->
            val jitter = Random.nextInt(-2, 3)
            val newRssi = (net.rssi + jitter).coerceIn(-95, -30)
            net.copy(
                rssi = newRssi,
                timestamp = System.currentTimeMillis()
            )
        }.sortedByDescending { it.rssi }

        synchronized(publicationLock) {
            if (disposed || !_isSimulatedMode.value) return
            _networks.value = updated
            _connectedNetwork.value = updated.find { it.isConnected }
        }
    }

    private fun createApartmentBuildingNeighbors(): List<WifiNetwork> {
        return listOf(
            WifiNetwork(
                ssid = "Apt304_Home_5G",
                bssid = "D8:07:B6:11:22:33",
                rssi = -46,
                frequency = 5240, // 5GHz Ch 48
                channelWidthMhz = 80,
                capabilities = "[WPA3-SAE-CCMP][WPA2-PSK-CCMP][ESS]",
                isConnected = true,
                linkSpeedMbps = 866,
                ipAddress = "192.168.1.142"
            ),
            WifiNetwork(
                ssid = "Apt304_Home_2.4G",
                bssid = "D8:07:B6:11:22:34",
                rssi = -48,
                frequency = 2437, // 2.4GHz Ch 6
                channelWidthMhz = 20,
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                isConnected = false,
                linkSpeedMbps = 144
            ),
            WifiNetwork(
                ssid = "Neighbor_305_NETGEAR",
                bssid = "20:E5:2A:44:11:88",
                rssi = -63, // Strong co-channel interference on Ch 6!
                frequency = 2437, // Ch 6
                channelWidthMhz = 20,
                capabilities = "[WPA2-PSK-CCMP][ESS]"
            ),
            WifiNetwork(
                ssid = "Spectrum_Setup_9B",
                bssid = "44:32:C8:77:AA:BB",
                rssi = -68, // Bad adjacent-channel overlap on Ch 4!
                frequency = 2427, // Ch 4 (overlaps Ch 1 & Ch 6)
                channelWidthMhz = 20,
                capabilities = "[WPA2-PSK-CCMP][ESS]"
            ),
            WifiNetwork(
                ssid = "Upstairs_404_Mesh",
                bssid = "00:11:32:99:FF:EE",
                rssi = -71,
                frequency = 5180, // 5GHz Ch 36
                channelWidthMhz = 80,
                capabilities = "[WPA3-SAE-CCMP][ESS]"
            ),
            WifiNetwork(
                ssid = "Downstairs_204_Fiber",
                bssid = "E4:AA:5D:88:99:11",
                rssi = -74,
                frequency = 2412, // 2.4GHz Ch 1
                channelWidthMhz = 20,
                capabilities = "[WPA2-PSK-CCMP][ESS]"
            ),
            WifiNetwork(
                ssid = "Hallway_Security_Cam",
                bssid = "A8:6D:AA:22:33:44",
                rssi = -77,
                frequency = 2437, // Ch 6
                channelWidthMhz = 20,
                capabilities = "[WPA2-PSK-CCMP][ESS]"
            ),
            WifiNetwork(
                ssid = "Neighbor_303_5G_Extender",
                bssid = "F0:9F:C2:55:66:77",
                rssi = -72,
                frequency = 5745, // 5GHz Ch 149
                channelWidthMhz = 40,
                capabilities = "[WPA2-PSK-CCMP][ESS]"
            ),
            WifiNetwork(
                ssid = "ATT-Fiber-Guest",
                bssid = "CC:D5:39:10:20:30",
                rssi = -82,
                frequency = 2462, // 2.4GHz Ch 11
                channelWidthMhz = 20,
                capabilities = "[WPA2-PSK-CCMP][ESS]"
            ),
            WifiNetwork(
                ssid = "SmartTV_LivingRoom_Direct",
                bssid = "5C:AA:FD:12:34:56",
                rssi = -55,
                frequency = 5200, // 5GHz Ch 40
                channelWidthMhz = 40,
                capabilities = "[WPA2-PSK-CCMP][WPS][ESS]"
            ),
            WifiNetwork(
                ssid = "XFINITY_FREE_WIFI",
                bssid = "68:B6:FC:99:88:77",
                rssi = -86,
                frequency = 2437, // Ch 6
                channelWidthMhz = 20,
                capabilities = "[OPEN]"
            )
        )
    }

    fun cleanup() {
        synchronized(publicationLock) {
            disposed = true
            _isScanning.value = false
            pollingJob?.cancel()
            pendingSimulationJob?.cancel()
        }
        unregisterReceiver()
    }
}
