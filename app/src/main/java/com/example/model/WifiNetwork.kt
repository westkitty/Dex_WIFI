package com.example.model

enum class WifiBand(val displayName: String, val frequencyRange: String) {
    BAND_2_4GHZ("2.4 GHz", "2400 - 2484 MHz"),
    BAND_5GHZ("5 GHz", "5150 - 5850 MHz"),
    BAND_6GHZ("6 GHz", "5925 - 7125 MHz");

    companion object {
        fun fromFrequency(freqMhz: Int): WifiBand {
            return when {
                freqMhz in 2400..2499 -> BAND_2_4GHZ
                freqMhz in 4900..5899 -> BAND_5GHZ
                freqMhz >= 5900 -> BAND_6GHZ
                else -> BAND_2_4GHZ
            }
        }

        fun frequencyToChannel(freqMhz: Int): Int {
            return when {
                freqMhz == 2484 -> 14
                freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
                freqMhz in 5170..5825 -> (freqMhz - 5000) / 5
                freqMhz in 5925..7125 -> (freqMhz - 5950) / 5
                else -> 1
            }
        }
    }
}

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int, // dBm, typically -30 (very close) to -90 (weak)
    val frequency: Int, // MHz
    val channel: Int = WifiBand.frequencyToChannel(frequency),
    val band: WifiBand = WifiBand.fromFrequency(frequency),
    val channelWidthMhz: Int = 20, // 20, 40, 80, 160
    val capabilities: String = "",
    val isConnected: Boolean = false,
    val linkSpeedMbps: Int? = null,
    val ipAddress: String? = null,
    val securityType: String = parseSecurity(capabilities),
    val timestamp: Long = System.currentTimeMillis()
) {
    val signalPercent: Int
        get() {
            // Map -100 dBm (0%) to -50 dBm (100%)
            val clamped = rssi.coerceIn(-100, -45)
            return ((clamped + 100) * 100) / 55
        }

    val estimatedDistanceMeters: Double
        get() {
            // Free-space path loss estimation formula: d = 10 ^ ((27.55 - (20 * log10(freq)) + abs(rssi)) / 20)
            val exp = (27.55 - (20 * Math.log10(frequency.toDouble())) + Math.abs(rssi)) / 20.0
            val raw = Math.pow(10.0, exp)
            return (Math.round(raw * 10.0) / 10.0).coerceIn(0.5, 45.0)
        }

    val qualityLevel: SignalQuality
        get() = when {
            rssi >= -55 -> SignalQuality.EXCELLENT
            rssi >= -67 -> SignalQuality.GOOD
            rssi >= -75 -> SignalQuality.FAIR
            rssi >= -85 -> SignalQuality.WEAK
            else -> SignalQuality.CRITICAL
        }

    companion object {
        fun parseSecurity(caps: String): String {
            return when {
                caps.contains("WPA3", ignoreCase = true) || caps.contains("SAE", ignoreCase = true) -> "WPA3 Personal"
                caps.contains("WPA2", ignoreCase = true) -> "WPA2 Personal"
                caps.contains("WPA", ignoreCase = true) -> "WPA"
                caps.contains("WEP", ignoreCase = true) -> "WEP"
                caps.contains("OPEN", ignoreCase = true) || caps.isEmpty() -> "Open"
                else -> "Secured"
            }
        }
    }
}

enum class SignalQuality(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    WEAK("Weak"),
    CRITICAL("Dead Zone")
}
