package com.example.service

import com.example.model.ApartmentLayout
import com.example.model.SurveyPoint
import com.example.model.WifiBand
import com.example.model.WifiNetwork
import kotlin.math.roundToInt

/** Heuristic, on-device analysis. Never claim inferred results are measured throughput. */
object RadioAuditEngine {
    enum class Sort { STRONGEST, WEAKEST, NAME, CHANNEL }
    data class Filter(
        val text: String = "", val band: WifiBand? = null,
        val minimumRssi: Int = -100, val sort: Sort = Sort.STRONGEST,
        val onePerSsid: Boolean = false, val includeHidden: Boolean = true,
        val insecureOnly: Boolean = false
    )
    data class ChannelChoice(val band: WifiBand, val channel: Int, val score: Int, val coChannelCount: Int)
    data class RoomStatus(val name: String, val count: Int, val worstRssi: Int?, val lastAt: Long?)
    data class Statistics(
        val networkCount: Int, val ssidCount: Int, val bssidCount: Int,
        val hiddenCount: Int, val openCount: Int, val wepCount: Int,
        val wpsCount: Int, val meshSsidCount: Int, val weakCount: Int,
        val overlapping24Count: Int, val oldestScanMs: Long?, val newestScanMs: Long?,
        val bands: Map<WifiBand, Int>
    )

    fun isHidden(n: WifiNetwork): Boolean = n.ssid.isBlank() || n.ssid == "<Hidden Network>"
    fun isInsecure(n: WifiNetwork): Boolean =
        n.securityType.equals("Open", true) || n.securityType.equals("WEP", true) ||
            n.capabilities.contains("[OPEN]", true)

    fun filter(networks: List<WifiNetwork>, options: Filter): List<WifiNetwork> {
        val q = options.text.trim()
        val matched = networks.asSequence()
            .filter { options.band == null || it.band == options.band }
            .filter { it.rssi >= options.minimumRssi }
            .filter { options.includeHidden || !isHidden(it) }
            .filter { !options.insecureOnly || isInsecure(it) }
            .filter {
                q.isEmpty() || it.ssid.contains(q, true) || it.bssid.contains(q, true) ||
                    it.securityType.contains(q, true)
            }.toList()
        val grouped = if (options.onePerSsid) matched.groupBy { it.ssid }.values.map { group ->
            group.maxByOrNull { it.rssi }!!
        } else matched
        return when (options.sort) {
            Sort.STRONGEST -> grouped.sortedByDescending { it.rssi }
            Sort.WEAKEST -> grouped.sortedBy { it.rssi }
            Sort.NAME -> grouped.sortedBy { it.ssid.lowercase() }
            Sort.CHANNEL -> grouped.sortedWith(compareBy({ it.band.ordinal }, { it.channel }, { -it.rssi }))
        }
    }

    fun statistics(networks: List<WifiNetwork>): Statistics {
        val times = networks.mapNotNull { it.timestamp.takeIf { t -> t > 0L } }
        return Statistics(
            networkCount = networks.size,
            ssidCount = networks.map { it.ssid }.distinct().size,
            bssidCount = networks.map { it.bssid.lowercase() }.distinct().size,
            hiddenCount = networks.count(::isHidden),
            openCount = networks.count { it.securityType.equals("Open", true) },
            wepCount = networks.count { it.securityType.equals("WEP", true) },
            wpsCount = networks.count { it.capabilities.contains("WPS", true) },
            meshSsidCount = networks.groupBy { it.ssid }.count { (ssid, aps) ->
                ssid.isNotBlank() && aps.map { it.bssid.lowercase() }.distinct().size > 1
            },
            weakCount = networks.count { it.rssi < -75 },
            overlapping24Count = networks.count {
                it.band == WifiBand.BAND_2_4GHZ && it.channel !in setOf(1, 6, 11, 14)
            },
            oldestScanMs = times.minOrNull(),
            newestScanMs = times.maxOrNull(),
            bands = WifiBand.entries.associateWith { band -> networks.count { it.band == band } }
        )
    }

    /** 6 GHz candidates require regional regulatory data absent here. Show observed use only. */
    fun channelChoices(networks: List<WifiNetwork>, band: WifiBand): List<ChannelChoice> {
        if (band == WifiBand.BAND_6GHZ) return emptyList()
        return WifiAnalyzerEngine.analyzeChannels(networks, band)
            .filter { it.isNonOverlappingStandard }
            .map { ChannelChoice(band, it.channel, it.bestScore, it.coChannelCount) }
            .sortedWith(compareByDescending<ChannelChoice> { it.score }.thenBy { it.channel })
    }

    fun observedChannels(networks: List<WifiNetwork>, band: WifiBand): List<Pair<Int, Int>> =
        networks.filter { it.band == band }.groupingBy { it.channel }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })

    fun rooms(layout: ApartmentLayout, points: List<SurveyPoint>): List<RoomStatus> =
        layout.rooms.map { room ->
            val readings = points.filter { it.roomName.equals(room.name, ignoreCase = true) }
            RoomStatus(room.name, readings.size, readings.minOfOrNull { it.rssi }, readings.maxOfOrNull { it.timestamp })
        }

    fun sampleCountByBand(points: List<SurveyPoint>): Map<WifiBand, Int> =
        WifiBand.entries.associateWith { band ->
            points.count { WifiBand.fromFrequency(it.frequency) == band }
        }

    fun weakSurveyPoints(points: List<SurveyPoint>, threshold: Int = -75): List<SurveyPoint> =
        points.filter { it.rssi < threshold }.sortedBy { it.rssi }

    fun staleSurveyPoints(points: List<SurveyPoint>, nowMs: Long, days: Int = 14): List<SurveyPoint> =
        points.filter { it.timestamp <= 0L || nowMs - it.timestamp > days * 86_400_000L }

    fun rssiSpread(history: List<Int>): Int? =
        history.takeIf { it.size >= 3 }?.let { it.max() - it.min() }

    fun rssiMedian(history: List<Int>): Int? {
        if (history.isEmpty()) return null
        val sorted = history.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else ((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0).roundToInt()
    }

    fun signalQualityTag(rssi: Int): String = when {
        rssi >= -55 -> "Strong"
        rssi >= -67 -> "Good"
        rssi >= -75 -> "Fair"
        rssi >= -85 -> "Weak"
        else -> "Critical"
    }

    fun ageLabel(timestamp: Long, nowMs: Long): String = when {
        timestamp <= 0L -> "Age unknown"
        timestamp > nowMs -> "Clock mismatch"
        nowMs - timestamp < 1_000L -> "Just sampled"
        nowMs - timestamp < 60_000L -> ((nowMs - timestamp) / 1_000L).toString() + "s ago"
        else -> ((nowMs - timestamp) / 60_000L).toString() + "m ago"
    }

    private fun safeCsv(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        val leading = clean.trimStart()
        val guarded = if (listOf("=", "+", "-", "@").any { leading.startsWith(it) }) "'" + clean else clean
        return "\"" + guarded.replace("\"", "\"\"") + "\""
    }

    fun exportSurveyCsv(points: List<SurveyPoint>, source: String): String = buildString {
        appendLine("# Dex_WIFI survey export - " + source.replace("\n", " "))
        appendLine("# RSSI snapshots; not lab calibrated and not proof of router configuration")
        appendLine("id,layout,room,ssid,rssi_dbm,frequency_mhz,channel,x_normalized,y_normalized,measured_at_ms,notes")
        points.forEach { p ->
            appendLine(listOf(
                p.id.toString(), safeCsv(p.layoutId), safeCsv(p.roomName), safeCsv(p.primarySsid),
                p.rssi.toString(), p.frequency.toString(), p.channel.toString(),
                p.position.x.toString(), p.position.y.toString(), p.timestamp.toString(),
                safeCsv(p.notes)
            ).joinToString(","))
        }
    }

    /** No raw SSIDs, passwords, MACs, floor-plan images or map coordinates leave by default. */
    fun shareableSummary(networks: List<WifiNetwork>, points: List<SurveyPoint>, simulated: Boolean): String {
        val stats = statistics(networks)
        return buildString {
            appendLine("Dex_WIFI local audit")
            appendLine("Source: " + if (simulated) "SIMULATED DEMO, NOT APARTMENT MEASUREMENTS"
                else "Android hardware, not independent router verification")
            appendLine("Visible APs: " + stats.networkCount + ", SSIDs: " + stats.ssidCount +
                ", multi-AP SSIDs: " + stats.meshSsidCount)
            appendLine("Bands: " + stats.bands.entries.joinToString { it.key.displayName + " " + it.value })
            appendLine("Security flags: open " + stats.openCount + ", WEP " + stats.wepCount + ", WPS " + stats.wpsCount)
            appendLine("Neighbor overlap flags: " + stats.overlapping24Count)
            appendLine("Survey points: " + points.size + ", weak (<-75 dBm): " + weakSurveyPoints(points).size)
            appendLine("Channel hints are heuristic; user must verify supported router settings.")
        }
    }
}
