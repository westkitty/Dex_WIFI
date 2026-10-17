package com.example.service

import com.example.model.ApartmentLayout
import com.example.model.FloorPlanDocument
import com.example.model.NormalizedPoint
import com.example.model.PlanGeometry
import com.example.model.SurveyPoint
import com.example.model.WifiBand
import kotlin.math.hypot

/**
 * Spatial survey quality, NOT an RF propagation model.
 * Samples are stored hardware readings from the custom measured plan. No generated RSSI.
 * Only an SSID+band cohort may contribute to its proximity coverage estimate.
 */
object SurveyCoachEngine {
    data class Cohort(val ssid: String, val band: WifiBand) {
        val label: String get() = ssid + " / " + band.displayName
    }
    data class Waypoint(
        val point: NormalizedPoint,
        val room: String,
        val nearestSampleMeters: Float
    )
    data class RoomProgress(
        val room: String,
        val samples: Int,
        val requiresAttention: Boolean
    )
    data class Report(
        val eligible: Boolean,
        val reason: String,
        val scannedReadings: Int,
        val selectedReadings: Int,
        val freshReadings: Int,
        val invalidReadings: Int,
        val staleReadings: Int,
        val futureReadings: Int,
        val mismatchedReadings: Int,
        val outOfBounds: Int,
        val duplicateLocations: Int,
        val changedChannelReadings: Int,
        val weakReadings: Int,
        val proximityPercent: Int?,
        val uncoveredPercent: Int?,
        val gridCells: Int,
        val coveredCells: Int,
        val medianSpacingMeters: Float?,
        val edgeSampleCount: Int,
        val unmeasuredRooms: List<RoomProgress>,
        val roomProgress: List<RoomProgress>,
        val suggestedNext: List<Waypoint>,
        val warnings: List<String>
    )

    fun availableCohorts(points: List<SurveyPoint>): List<Cohort> =
        points.asSequence().filter { it.primarySsid.isNotBlank() && it.frequency > 0 }
            .groupBy { Cohort(it.primarySsid, WifiBand.fromFrequency(it.frequency)) }
            .toList().sortedByDescending { (_, group) -> group.maxOf { it.timestamp } }
            .map { it.first }

    private fun validSample(p: SurveyPoint): Boolean =
        p.primarySsid.isNotBlank() && p.rssi in -100..-1 &&
            p.frequency in 2400..7125 && p.position.x.isFinite() &&
            p.position.y.isFinite() && p.position.x in 0f..1f &&
            p.position.y in 0f..1f && p.timestamp > 0

    fun analyze(
        layout: ApartmentLayout,
        readings: List<SurveyPoint>,
        cohort: Cohort? = availableCohorts(readings).firstOrNull(),
        nowMillis: Long,
        maxAgeDays: Int = 14,
        radiusMeters: Float = 3.5f,
        columns: Int = 20,
        rows: Int = 16
    ): Report {
        require(columns in 2..48 && rows in 2..48)
        require(maxAgeDays in 1..365)
        require(radiusMeters.isFinite() && radiusMeters in .5f..10f)
        val isCustomPlan = layout.id == FloorPlanDocument.LAYOUT_ID
        val geometryReady = isCustomPlan && layout.referenceCalibrated &&
            layout.widthMeters.isFinite() && layout.widthMeters > 0f &&
            layout.heightMeters.isFinite() && layout.heightMeters > 0f
        val cohortReadings = readings.filter {
            cohort != null && it.primarySsid == cohort.ssid &&
                it.frequency > 0 && WifiBand.fromFrequency(it.frequency) == cohort.band
        }
        // Bound work on very large imported surveys without silently claiming completeness.
        val limited = cohortReadings.sortedByDescending { it.timestamp }.take(400)
        val invalid = limited.count { !validSample(it) }
        val sane = limited.filter(::validSample)
        val staleLimit = maxAgeDays.toLong() * 86_400_000L
        val future = sane.count { it.timestamp > nowMillis + 300_000L }
        val stale = sane.count { nowMillis - it.timestamp > staleLimit }
        val fresh = sane.filter {
            it.timestamp <= nowMillis + 300_000L && nowMillis - it.timestamp <= staleLimit
        }
        val outside = fresh.count {
            !PlanGeometry.pointInOutline(it.position, layout.outline)
        }
        val usable = fresh.filter { PlanGeometry.pointInOutline(it.position, layout.outline) }
        val deduped = mutableListOf<SurveyPoint>()
        var duplicates = 0
        if (geometryReady) {
            for (sample in usable) {
                if (deduped.any { PlanGeometry.distanceMeters(
                        it.position, sample.position, layout.widthMeters, layout.heightMeters
                    ) < 0.60f }) duplicates++ else deduped.add(sample)
            }
        } else deduped.addAll(usable)
        val mostRecentChannel = usable.maxByOrNull { it.timestamp }?.channel
        val changes = if (mostRecentChannel == null) 0 else usable.count { it.channel != mostRecentChannel }
        val rooms = layout.rooms.map { room ->
            val n = deduped.count { point ->
                point.position.x in room.leftNorm..(room.leftNorm + room.widthNorm) &&
                    point.position.y in room.topNorm..(room.topNorm + room.heightNorm)
            }
            RoomProgress(room.name, n, n < 2)
        }
        val warnings = mutableListOf<String>()
        if (!isCustomPlan) warnings.add("Example layouts may include synthetic data: no physical-coverage claim.")
        else if (!geometryReady) warnings.add("Calibrate the real apartment dimensions before spatial estimates.")
        if (layout.outline.size < 3) warnings.add("No closed perimeter: proximity estimates use the entire rectangular map.")
        if (readings.size > cohortReadings.size) warnings.add("Other SSIDs or frequency bands exist; excluded from this cohort.")
        if (cohortReadings.size > 400) warnings.add("Showing the newest 400 readings to keep analysis responsive.")
        if (invalid > 0) warnings.add("Invalid readings excluded from spatial analysis.")
        if (stale > 0) warnings.add("Older readings excluded using your age limit.")
        if (future > 0) warnings.add("Readings with future timestamps excluded; check device clock.")
        if (outside > 0) warnings.add("Readings outside the traced perimeter excluded.")
        if (duplicates > 0) warnings.add("Several readings cluster within 0.6 m; visit different positions.")
        if (changes > 0) warnings.add("Channel changes in this cohort: compare same AP/band before drawing conclusions.")
        warnings.add("Saved survey points lack BSSID identity. Different mesh APs with one SSID cannot be separated.")
        val nearest = mutableListOf<Pair<NormalizedPoint, Float>>()
        var covered = 0
        if (geometryReady) {
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val cell = NormalizedPoint((col + .5f) / columns, (row + .5f) / rows)
                    if (!PlanGeometry.pointInOutline(cell, layout.outline)) continue
                    val distance = deduped.minOfOrNull {
                        PlanGeometry.distanceMeters(it.position, cell,
                            layout.widthMeters, layout.heightMeters)
                    } ?: Float.POSITIVE_INFINITY
                    nearest.add(cell to distance)
                    if (distance <= radiusMeters) covered++
                }
            }
        }
        val suggestions = mutableListOf<Waypoint>()
        if (geometryReady) {
            for ((cell, distance) in nearest.sortedByDescending { it.second }) {
                if (suggestions.size >= 5) break
                if (suggestions.any { PlanGeometry.distanceMeters(it.point, cell,
                        layout.widthMeters, layout.heightMeters) < 1.5f }) continue
                suggestions.add(Waypoint(cell, layout.rooms.lastOrNull { r ->
                    cell.x in r.leftNorm..(r.leftNorm + r.widthNorm) &&
                        cell.y in r.topNorm..(r.topNorm + r.heightNorm)
                }?.name ?: "Unlabelled area", distance))
            }
        }
        val spacing = if (geometryReady && deduped.size >= 2) {
            val distances = deduped.map { point ->
                deduped.asSequence().filterNot { it === point }.minOf {
                    PlanGeometry.distanceMeters(point.position, it.position,
                        layout.widthMeters, layout.heightMeters)
                }
            }.sorted()
            distances[distances.size / 2]
        } else null
        val percent = if (nearest.isEmpty() || cohort == null || deduped.isEmpty()) null else
            ((covered * 100.0) / nearest.size).toInt().coerceIn(0, 100)
        return Report(
            eligible = geometryReady && cohort != null && deduped.isNotEmpty(),
            reason = if (!isCustomPlan) "Select your saved custom floor plan"
                     else if (!geometryReady) "Set verified physical dimensions in Floor Plan Studio"
                     else if (cohort == null) "Capture your first actual Wi-Fi measurement"
                     else "Spatial proximity only; not verified Wi-Fi coverage",
            scannedReadings = readings.size,
            selectedReadings = cohortReadings.size,
            freshReadings = usable.size,
            invalidReadings = invalid,
            staleReadings = stale,
            futureReadings = future,
            mismatchedReadings = readings.size - cohortReadings.size,
            outOfBounds = outside,
            duplicateLocations = duplicates,
            changedChannelReadings = changes,
            weakReadings = usable.count { it.rssi < -75 },
            proximityPercent = percent,
            uncoveredPercent = percent?.let { 100 - it },
            gridCells = nearest.size,
            coveredCells = covered,
            medianSpacingMeters = spacing,
            edgeSampleCount = usable.count {
                it.position.x < .12f || it.position.x > .88f ||
                    it.position.y < .12f || it.position.y > .88f
            },
            unmeasuredRooms = rooms.filter { it.requiresAttention },
            roomProgress = rooms,
            suggestedNext = suggestions,
            warnings = warnings
        )
    }
}
