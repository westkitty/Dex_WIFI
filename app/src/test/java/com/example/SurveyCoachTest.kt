package com.example

import com.example.model.ApartmentLayout
import com.example.model.FloorPlanDocument
import com.example.model.NormalizedPoint
import com.example.model.RoomZone
import com.example.model.SurveyPoint
import com.example.model.WifiBand
import com.example.service.ScanFreshness
import com.example.service.SurveyCoachEngine
import org.junit.Assert.*
import org.junit.Test

class SurveyCoachTest {
    private val layout = ApartmentLayout(
        id = FloorPlanDocument.LAYOUT_ID,
        name = "Actual apartment",
        description = "Measured",
        widthMeters = 10f,
        heightMeters = 8f,
        referenceCalibrated = true,
        rooms = listOf(RoomZone("room1", "Living", 0f, 0f, .5f, 1f),
            RoomZone("room2", "Kitchen", .5f, 0f, .5f, 1f))
    )
    private val now = 1_800_000_000_000L
    private fun p(
        id: Long, x: Float, y: Float, ssid: String = "Home",
        freq: Int = 5180, time: Long = now - 60_000L, rssi: Int = -65
    ) = SurveyPoint(
        id, FloorPlanDocument.LAYOUT_ID, NormalizedPoint(x, y),
        if (x < .5f) "Living" else "Kitchen", ssid, rssi,
        freq, WifiBand.frequencyToChannel(freq), time
    )

    @Test fun scanTimestampIsMonotonicNotWallClock() {
        assertEquals(1500L, ScanFreshness.ageMillis(10_000L, 8_500_000L))
        assertEquals(98_500L, ScanFreshness.wallClockMillis(100_000L, 10_000L, 8_500_000L))
        assertNull(ScanFreshness.ageMillis(500L, 2_000_000L))
        assertNull(ScanFreshness.ageMillis(100L, 0L))
        assertNull(ScanFreshness.ageMillis(200_000_000L, 1_000_000L))
    }

    @Test fun emptySurveyCannotProduceFakeCoverage() {
        val result = SurveyCoachEngine.analyze(layout, emptyList(), nowMillis = now)
        assertEquals(0, result.selectedReadings)
        assertNull(result.proximityPercent)
        assertFalse(result.eligible)
        assertTrue(result.suggestedNext.isNotEmpty())
    }

    @Test fun uncalibratedPlanCannotReportPercent() {
        val result = SurveyCoachEngine.analyze(
            layout.copy(referenceCalibrated = false),
            listOf(p(1, .1f, .2f)), nowMillis = now
        )
        assertFalse(result.eligible)
        assertNull(result.proximityPercent)
        assertEquals(0, result.gridCells)
    }

    @Test fun selectionNeverBlendsBandsOrSsid() {
        val readings = listOf(
            p(1, .1f, .1f), p(2, .9f, .9f, ssid = "Other"),
            p(3, .5f, .5f, freq = 2437)
        )
        val report = SurveyCoachEngine.analyze(layout, readings,
            cohort = SurveyCoachEngine.Cohort("Home", WifiBand.BAND_5GHZ), nowMillis = now)
        assertEquals(1, report.selectedReadings)
        assertEquals(2, report.mismatchedReadings)
        assertTrue(report.proximityPercent != null)
    }

    @Test fun duplicateAndOldOrFutureReadingsAreSeparated() {
        val readings = listOf(
            p(1, .2f, .3f),
            p(2, .2001f, .3001f),
            p(3, .5f, .5f, time = now - 20L * 86_400_000L),
            p(4, .7f, .5f, time = now + 400_000L),
            p(5, -1f, .5f)
        )
        val result = SurveyCoachEngine.analyze(layout, readings, nowMillis = now)
        assertEquals(1, result.duplicateLocations)
        assertEquals(1, result.staleReadings)
        assertEquals(1, result.futureReadings)
        assertEquals(1, result.invalidReadings)
        assertEquals(2, result.freshReadings)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test fun suggestedLocationsRemainInsideOutlineAndCoverageIsBounded() {
        val triangular = layout.copy(outline = listOf(
            NormalizedPoint(0f,0f), NormalizedPoint(1f,0f), NormalizedPoint(0f,1f)
        ))
        val result = SurveyCoachEngine.analyze(triangular,
            listOf(p(1, .1f, .1f), p(2, .2f, .2f)), nowMillis = now)
        assertTrue(result.proximityPercent!! in 0..100)
        assertTrue(result.suggestedNext.all {
            com.example.model.PlanGeometry.pointInOutline(it.point, triangular.outline)
        })
    }
}
