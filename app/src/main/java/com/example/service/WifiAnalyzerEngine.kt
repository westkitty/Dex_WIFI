package com.example.service

import com.example.model.ApartmentLayout
import com.example.model.ChannelAnalysis
import com.example.model.ChannelSpectrumCurve
import com.example.model.SuggestionCategory
import com.example.model.SuggestionSeverity
import com.example.model.SurveyPoint
import com.example.model.WifiBand
import com.example.model.WifiNetwork
import com.example.model.WifiOptimizationSuggestion
import kotlin.math.abs
import kotlin.math.max

object WifiAnalyzerEngine {

    // Standard 2.4 GHz channel centers
    val standard24Channels = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
    val standard5Channels = listOf(36, 40, 44, 48, 149, 153, 157, 161, 165)

    fun analyzeChannels(networks: List<WifiNetwork>, band: WifiBand): List<ChannelAnalysis> {
        val targetChannels = if (band == WifiBand.BAND_2_4GHZ) standard24Channels else standard5Channels

        return targetChannels.map { channel ->
            val centerFreq = if (band == WifiBand.BAND_2_4GHZ) 2407 + channel * 5 else 5000 + channel * 5
            val coChannelNets = networks.filter { it.band == band && it.channel == channel }

            var adjacentInterference = 0
            if (band == WifiBand.BAND_2_4GHZ) {
                // In 2.4 GHz, each 20 MHz channel spans +/- 11 MHz (overlaps +/- 2 to 3 channels)
                networks.filter { it.band == WifiBand.BAND_2_4GHZ && it.channel != channel }.forEach { net ->
                    val diff = abs(net.channel - channel)
                    if (diff <= 4) {
                        // Closer channels bleed more energy
                        val weight = (5 - diff)
                        val powerFactor = ((net.rssi + 100) / 10).coerceAtLeast(1)
                        adjacentInterference += weight * powerFactor
                    }
                }
            } else {
                // In 5GHz, check 40/80 MHz channel overlap
                networks.filter { it.band == WifiBand.BAND_5GHZ && it.channel != channel }.forEach { net ->
                    val diff = abs(net.channel - channel)
                    if (diff <= 8 && net.channelWidthMhz >= 80) {
                        adjacentInterference += 15
                    }
                }
            }

            // Calculate co-channel interference penalty
            var coChannelPenalty = 0
            coChannelNets.forEach { net ->
                // Stronger signals cause more immediate contention
                val strength = ((net.rssi + 100) * 1.5).toInt()
                coChannelPenalty += strength
            }

            val totalInterference = coChannelPenalty + adjacentInterference
            // Score from 0 to 100 (100 = completely clean channel)
            val score = (100 - totalInterference).coerceIn(10, 100)

            ChannelAnalysis(
                channel = channel,
                band = band,
                centerFreqMhz = centerFreq,
                coChannelCount = coChannelNets.size,
                adjacentInterferenceScore = adjacentInterference,
                networksOnChannel = coChannelNets,
                bestScore = score,
                isRecommended = false // determined after sorting
            )
        }.let { list ->
            // Mark the highest scoring standard channel as recommended
            val maxScore = list.filter { it.isNonOverlappingStandard }.maxOfOrNull { it.bestScore } ?: 0
            list.map { analysis ->
                if (analysis.isNonOverlappingStandard && analysis.bestScore == maxScore) {
                    analysis.copy(isRecommended = true)
                } else {
                    analysis
                }
            }
        }
    }

    fun generateSpectrumCurves(networks: List<WifiNetwork>, band: WifiBand): List<ChannelSpectrumCurve> {
        val filtered = networks.filter { it.band == band }
        return filtered.mapIndexed { index, net ->
            ChannelSpectrumCurve(
                channel = net.channel,
                centerFreqMhz = net.frequency,
                band = net.band,
                bandwidthMhz = net.channelWidthMhz,
                peakRssi = net.rssi,
                ssid = net.ssid,
                bssid = net.bssid,
                isConnected = net.isConnected,
                colorIndex = index % 8
            )
        }
    }

    fun generateOptimizationSuggestions(
        networks: List<WifiNetwork>,
        connected: WifiNetwork?,
        surveyPoints: List<SurveyPoint>,
        layout: ApartmentLayout?
    ): List<WifiOptimizationSuggestion> {
        val suggestions = mutableListOf<WifiOptimizationSuggestion>()

        // 1. Analyze Connected Network Channel Congestion
        if (connected != null) {
            val bandAnalyses = analyzeChannels(networks, connected.band)
            val currentChannelAnalysis = bandAnalyses.find { it.channel == connected.channel }
            val recommendedChannel = bandAnalyses.filter { it.isNonOverlappingStandard }.maxByOrNull { it.bestScore }

            if (currentChannelAnalysis != null && recommendedChannel != null && recommendedChannel.channel != connected.channel) {
                if (currentChannelAnalysis.coChannelCount > 1 || currentChannelAnalysis.bestScore < 65) {
                    suggestions.add(
                        WifiOptimizationSuggestion(
                            id = "sugg_change_channel",
                            title = "High Interference on Channel ${connected.channel}",
                            summary = "Your network is sharing Channel ${connected.channel} with ${currentChannelAnalysis.coChannelCount} other networks.",
                            impact = "Up to +35% lower latency and fewer gaming/call dropouts",
                            severity = if (currentChannelAnalysis.bestScore < 40) SuggestionSeverity.CRITICAL else SuggestionSeverity.WARNING,
                            category = SuggestionCategory.CHANNEL_SELECTION,
                            recommendedAction = "Log in to your router settings and change the ${connected.band.displayName} Wi-Fi channel from ${connected.channel} to Channel ${recommendedChannel.channel}.",
                            currentSetting = "Channel ${connected.channel} (Score ${currentChannelAnalysis.bestScore}/100)",
                            suggestedSetting = "Channel ${recommendedChannel.channel} (Score ${recommendedChannel.bestScore}/100)"
                        )
                    )
                }
            }

            // 2. 2.4 GHz vs 5 GHz Band Steering
            if (connected.band == WifiBand.BAND_2_4GHZ) {
                val has5GhzAlternative = networks.any {
                    it.band == WifiBand.BAND_5GHZ && (it.ssid.contains(connected.ssid.take(6), ignoreCase = true) || it.rssi >= -68)
                }
                suggestions.add(
                    WifiOptimizationSuggestion(
                        id = "sugg_band_5ghz",
                        title = "Connected to Crowded 2.4 GHz Band",
                        summary = "You are currently on 2.4 GHz which suffers from heavy apartment interference (microwaves, Bluetooth, neighbors).",
                        impact = "3x to 5x higher download speeds and 5x wider bandwidth",
                        severity = SuggestionSeverity.OPTIMIZATION,
                        category = SuggestionCategory.BAND_UPGRADE,
                        recommendedAction = if (has5GhzAlternative) {
                            "Switch to your router's 5 GHz network (often named with '_5G') or enable Band Steering in router settings."
                        } else {
                            "Ensure 5 GHz is enabled in your router setup for high speed streaming."
                        },
                        currentSetting = "2.4 GHz (~50-100 Mbps max)",
                        suggestedSetting = "5 GHz (400-900+ Mbps capable)"
                    )
                )
            }

            // 3. 2.4 GHz 40MHz width warning (Causes massive apartment overlap)
            if (connected.band == WifiBand.BAND_2_4GHZ && connected.channelWidthMhz > 20) {
                suggestions.add(
                    WifiOptimizationSuggestion(
                        id = "sugg_channel_width_24",
                        title = "2.4 GHz Channel Width Set to ${connected.channelWidthMhz} MHz",
                        summary = "Using 40 MHz on 2.4 GHz occupies nearly the entire spectrum in an apartment building, colliding with almost all neighbors.",
                        impact = "More stable connection, fewer dropped packets",
                        severity = SuggestionSeverity.WARNING,
                        category = SuggestionCategory.CHANNEL_WIDTH,
                        recommendedAction = "Set 2.4 GHz channel width to '20 MHz only' instead of '20/40 MHz auto' in router settings.",
                        currentSetting = "${connected.channelWidthMhz} MHz wide",
                        suggestedSetting = "20 MHz standard"
                    )
                )
            }
        }

        // 4. Non-Standard 2.4 GHz Neighbor bleed warning
        val badNeighbors = networks.filter {
            it.band == WifiBand.BAND_2_4GHZ && it.channel !in listOf(1, 6, 11, 14) && it.rssi >= -75
        }
        if (badNeighbors.isNotEmpty()) {
            val sample = badNeighbors.first()
            suggestions.add(
                WifiOptimizationSuggestion(
                    id = "sugg_bad_neighbor_bleed",
                    title = "Neighbor Overlap from Channel ${sample.channel}",
                    summary = "Neighbor network '${sample.ssid}' (${sample.rssi} dBm) is on Channel ${sample.channel}. Non-standard channels 2, 3, 4, 5, 7, 8, 9, 10 produce adjacent channel noise.",
                    impact = "Prevents RF packet corruption and packet retries",
                    severity = SuggestionSeverity.WARNING,
                    category = SuggestionCategory.CHANNEL_SELECTION,
                    recommendedAction = if (sample.channel <= 5) {
                        "Stick strictly to Channel 11 on your router to stay far away from Channel ${sample.channel} energy bleed."
                    } else {
                        "Stick to Channel 1 on your router to avoid the upper spectrum overlap."
                    },
                    currentSetting = "Neighbor on Ch ${sample.channel}",
                    suggestedSetting = "Use opposite non-overlapping anchor (Ch 1 or 11)"
                )
            )
        }

        // 5. Apartment Dead Zone Detection from Survey Points
        val deadZones = surveyPoints.filter { it.rssi < -78 }
        if (deadZones.isNotEmpty()) {
            val worst = deadZones.minByOrNull { it.rssi }!!
            suggestions.add(
                WifiOptimizationSuggestion(
                    id = "sugg_dead_zone_${worst.roomName}",
                    title = "Dead Zone Detected in ${worst.roomName}",
                    summary = "Measured signal in ${worst.roomName} drops to ${worst.rssi} dBm (below reliable threshold). Wall attenuation is high.",
                    impact = "Eliminate video buffering and disconnects in ${worst.roomName}",
                    severity = SuggestionSeverity.CRITICAL,
                    category = SuggestionCategory.DEAD_ZONE_FIX,
                    recommendedAction = "Move your primary router toward the hallway/central living room, or place a Wi-Fi 6 Mesh node halfway between the router and ${worst.roomName}.",
                    currentSetting = "${worst.roomName}: ${worst.rssi} dBm (Critical)",
                    suggestedSetting = "Target > -65 dBm across all rooms"
                )
            )
        } else if (surveyPoints.isNotEmpty() && surveyPoints.all { it.rssi >= -65 }) {
            suggestions.add(
                WifiOptimizationSuggestion(
                    id = "sugg_coverage_optimal",
                    title = "Excellent Apartment Coverage",
                    summary = "All surveyed rooms maintain strong signal (> -65 dBm). Router position is well-balanced across the floor plan.",
                    impact = "Optimal apartment performance",
                    severity = SuggestionSeverity.GOOD,
                    category = SuggestionCategory.ROUTER_POSITION,
                    recommendedAction = "No router movement needed. Your current layout has minimal attenuation.",
                    currentSetting = "All rooms > -65 dBm",
                    suggestedSetting = "Optimal"
                )
            )
        }

        // 6. Router Placement Recommendation if living room or edge
        if (layout != null) {
            suggestions.add(
                WifiOptimizationSuggestion(
                    id = "sugg_router_elevation",
                    title = "Router Elevation & Line-of-Sight",
                    summary = "Wi-Fi signals degrade quickly through kitchen appliances (refrigerators, microwaves) and bathroom tile/mirrors.",
                    impact = "+3 to +6 dBm signal gain through walls",
                    severity = SuggestionSeverity.OPTIMIZATION,
                    category = SuggestionCategory.ROUTER_POSITION,
                    recommendedAction = "Elevate router at least 4-5 feet off the floor on a shelf or open table. Keep at least 3 feet away from smart TV back panels, metal bookends, or thick concrete pillars.",
                    currentSetting = "Floor / TV corner",
                    suggestedSetting = "Elevated open shelf"
                )
            )
        }

        return suggestions
    }
}
