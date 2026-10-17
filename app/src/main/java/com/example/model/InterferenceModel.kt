package com.example.model

data class ChannelAnalysis(
    val channel: Int,
    val band: WifiBand,
    val centerFreqMhz: Int,
    val coChannelCount: Int, // networks sharing this exact channel
    val adjacentInterferenceScore: Int, // overlap from +/- 1, 2 channels
    val networksOnChannel: List<WifiNetwork>,
    val bestScore: Int, // 0 - 100 rating (100 = cleanest channel)
    val isRecommended: Boolean,
    val isNonOverlappingStandard: Boolean = (band == WifiBand.BAND_2_4GHZ && channel in listOf(1, 6, 11)) || band == WifiBand.BAND_5GHZ
)

data class ChannelSpectrumCurve(
    val channel: Int,
    val centerFreqMhz: Int,
    val band: WifiBand,
    val bandwidthMhz: Int,
    val peakRssi: Int,
    val ssid: String,
    val bssid: String,
    val isConnected: Boolean,
    val colorIndex: Int
)

enum class SuggestionSeverity {
    CRITICAL,
    WARNING,
    OPTIMIZATION,
    GOOD
}

enum class SuggestionCategory {
    CHANNEL_SELECTION,
    BAND_UPGRADE,
    ROUTER_POSITION,
    CHANNEL_WIDTH,
    DEAD_ZONE_FIX
}

data class WifiOptimizationSuggestion(
    val id: String,
    val title: String,
    val summary: String,
    val impact: String,
    val severity: SuggestionSeverity,
    val category: SuggestionCategory,
    val recommendedAction: String,
    val currentSetting: String? = null,
    val suggestedSetting: String? = null
)
