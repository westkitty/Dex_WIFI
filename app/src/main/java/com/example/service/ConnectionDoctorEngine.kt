package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.example.model.WifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlin.math.abs

/**
 * Read-only connectivity facts + explicitly requested probes.
 * No settings mutations, device discovery sweeps, packet capture, or hidden telemetry.
 */
object ConnectionDoctorEngine {
    data class Link(
        val activeNetwork: Boolean,
        val wifiDefault: Boolean,
        val osValidated: Boolean,
        val captivePortal: Boolean,
        val metered: Boolean,
        val gatewayUrl: String?,
        val dnsServers: List<String>,
        val interfaceName: String?,
        val mtu: Int?,
        val downstreamKbps: Int?
    )

    data class Observation(
        val ssid: String,
        val bssid: String,
        val band: String,
        val channel: Int,
        val rssi: Int,
        val linkMbps: Int?,
        val capturedAt: Long
    )

    enum class CompareState {
        MISSING_BASELINE, DEMO_BLOCKED, NEED_FRESH_SCAN,
        DIFFERENT_NETWORK, COMPARABLE
    }
    data class Comparison(
        val state: CompareState,
        val rssiDeltaDb: Int? = null,
        val linkRateDeltaMbps: Int? = null,
        val channelChanged: Boolean = false,
        val accessPointChanged: Boolean = false
    )

    fun observeConnected(network: WifiNetwork?, simulated: Boolean,
                         nowMillis: Long, maxAgeMillis: Long = 120_000L): Observation? {
        if (simulated || network == null || !network.isConnected ||
            network.ssid.isBlank() || network.frequency <= 0 ||
            network.rssi !in -100..-1 || network.timestamp <= 0L ||
            nowMillis - network.timestamp !in 0L..maxAgeMillis) return null
        return Observation(network.ssid, network.bssid, network.band.displayName,
            network.channel, network.rssi, network.linkSpeedMbps, nowMillis)
    }

    fun compare(before: Observation?, after: WifiNetwork?,
                simulated: Boolean, nowMillis: Long): Comparison {
        if (before == null) return Comparison(CompareState.MISSING_BASELINE)
        if (simulated) return Comparison(CompareState.DEMO_BLOCKED)
        if (after == null || !after.isConnected ||
            after.timestamp <= before.capturedAt ||
            after.timestamp <= 0L ||
            nowMillis - after.timestamp !in 0L..120_000L) {
            return Comparison(CompareState.NEED_FRESH_SCAN)
        }
        if (after.ssid != before.ssid || after.band.displayName != before.band) {
            return Comparison(CompareState.DIFFERENT_NETWORK)
        }
        return Comparison(
            CompareState.COMPARABLE,
            rssiDeltaDb = after.rssi - before.rssi,
            linkRateDeltaMbps = if (before.linkMbps != null && after.linkSpeedMbps != null)
                after.linkSpeedMbps - before.linkMbps else null,
            channelChanged = before.channel != after.channel,
            accessPointChanged = before.bssid != after.bssid
        )
    }

    fun connectionSnapshot(context: Context): Link {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return Link(false, false, false, false,
                false, null, emptyList(), null, null, null)
        val active = manager.activeNetwork ?: return Link(false, false, false, false,
            false, null, emptyList(), null, null, null)
        val capabilities = manager.getNetworkCapabilities(active)
        val properties = manager.getLinkProperties(active)
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val captive = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
        val unmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        return Link(
            activeNetwork = true,
            wifiDefault = wifi,
            osValidated = validated,
            captivePortal = captive,
            metered = !unmetered,
            gatewayUrl = if (wifi) RouterAdminPolicy.discoveredGateway(context) else null,
            dnsServers = properties?.dnsServers?.map { it.hostAddress ?: "Unavailable" } ?: emptyList(),
            interfaceName = properties?.interfaceName,
            mtu = properties?.mtu?.takeIf { it > 0 },
            downstreamKbps = capabilities?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }
        )
    }

    data class Probe(val success: Boolean, val durationMs: Long?,
                     val description: String)

    suspend fun testRouterWebPort(gatewayUrl: String?): Probe = withContext(Dispatchers.IO) {
        val safe = gatewayUrl?.let(RouterAdminPolicy::safeGatewayUrl)
            ?: return@withContext Probe(false, null, "No validated private gateway address.")
        try {
            val url = URI(safe)
            val port = if (url.port > 0) url.port else if (url.scheme == "https") 443 else 80
            val started = SystemClock.elapsedRealtime()
            Socket().use { s ->
                s.connect(InetSocketAddress(url.host, port), 1400)
            }
            Probe(true, SystemClock.elapsedRealtime() - started,
                "Router web TCP port answered. This does not prove Internet connectivity or admin login.")
        } catch (e: Exception) {
            Probe(false, null,
                "Router web port did not answer. Admin service may be disabled; this does not prove Wi-Fi failure.")
        }
    }

    suspend fun testDnsExplicitly(): Probe {
        val tested = withTimeoutOrNull(6000L) {
            withContext(Dispatchers.IO) {
                val started = SystemClock.elapsedRealtime()
                val ips = runCatching { InetAddress.getAllByName("example.com") }.getOrNull()
                if (ips.isNullOrEmpty()) Probe(false, null,
                    "DNS lookup for example.com returned no addresses.")
                else Probe(true, SystemClock.elapsedRealtime() - started,
                    "example.com resolved. DNS success does not prove Internet service quality.")
            }
        }
        return tested ?: Probe(false, null,
            "DNS lookup timed out after 6 seconds. No performance claim is possible.")
    }
}

/** Single private baseline; no router-admin credentials, traffic history, or cloud storage. */
class ConnectionBaselineStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "dex_wifi_diagnostics_baseline_v1", Context.MODE_PRIVATE)

    fun save(o: ConnectionDoctorEngine.Observation) {
        prefs.edit()
            .putString("ssid", o.ssid)
            .putString("bssid", o.bssid)
            .putString("band", o.band)
            .putInt("channel", o.channel)
            .putInt("rssi", o.rssi)
            .putInt("speed", o.linkMbps ?: -1)
            .putLong("at", o.capturedAt)
            .apply()
    }

    fun load(): ConnectionDoctorEngine.Observation? {
        if (!prefs.contains("at")) return null
        val ssid = prefs.getString("ssid", null) ?: return null
        val band = prefs.getString("band", null) ?: return null
        return ConnectionDoctorEngine.Observation(
            ssid, prefs.getString("bssid", "") ?: "", band,
            prefs.getInt("channel", 0), prefs.getInt("rssi", -100),
            prefs.getInt("speed", -1).takeIf { it >= 0 },
            prefs.getLong("at", 0L))
    }

    fun clear() { prefs.edit().clear().apply() }
}

