package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.URI

/** Browser handoff only. Credentials never enter Dex_WIFI. */
object RouterAdminPolicy {
    data class Baseline(val ssid: String, val band: String, val channel: Int, val observedAt: Long)
    enum class Verification { NO_REAL_DATA, NOT_YET_OBSERVED, DIFFERENT_NETWORK, STILL_OLD_CHANNEL, TARGET_CHANNEL_OBSERVED, UNEXPECTED_CHANNEL }

    fun safeGatewayUrl(input: String): String? {
        val value = input.trim()
        if (value.isBlank() || value.any { it.isWhitespace() }) return null
        val raw = if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "http://$value"
        return try {
            val uri = URI(raw)
            if (uri.scheme !in setOf("http", "https") || uri.userInfo != null ||
                uri.query != null || uri.fragment != null || (uri.path != "" && uri.path != "/") ||
                uri.port > 65535 || uri.port == 0) return null
            val host = uri.host ?: return null
            val parts = host.split('.').map { it.toIntOrNull() ?: return null }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return null
            val privateAddress = parts[0] == 10 ||
                (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 192 && parts[1] == 168)
            if (!privateAddress || parts[3] == 0 || parts[3] == 255) return null
            "${uri.scheme}://$host${if (uri.port > 0) ":${uri.port}" else ""}/"
        } catch (_: Exception) { null }
    }

    fun discoveredGateway(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        return cm.getLinkProperties(network)?.routes?.firstNotNullOfOrNull { route ->
            val gateway = route.gateway
            if (route.isDefaultRoute && gateway is Inet4Address) safeGatewayUrl(gateway.hostAddress ?: "")
            else null
        }
    }

    fun verify(
        baseline: Baseline?, targetChannel: Int?, ssid: String?, band: String?,
        channel: Int?, sampledAt: Long, simulated: Boolean
    ): Verification {
        if (simulated || baseline == null || targetChannel == null || ssid == null ||
            band == null || channel == null) return Verification.NO_REAL_DATA
        if (sampledAt <= baseline.observedAt || sampledAt <= 0) return Verification.NOT_YET_OBSERVED
        if (ssid != baseline.ssid || band != baseline.band) return Verification.DIFFERENT_NETWORK
        if (channel == baseline.channel && channel != targetChannel) return Verification.STILL_OLD_CHANNEL
        return if (channel == targetChannel) Verification.TARGET_CHANNEL_OBSERVED else Verification.UNEXPECTED_CHANNEL
    }
}
