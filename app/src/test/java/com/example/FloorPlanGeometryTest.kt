package com.example

import com.example.data.FloorPlanStore
import com.example.model.ApartmentLayout
import com.example.model.FloorPlanDocument
import com.example.model.NormalizedPoint
import com.example.model.PlanElementType
import com.example.model.PlanGeometry
import com.example.model.PlanSegment
import com.example.model.RoomZone
import com.example.model.RoomType
import com.example.model.SurveyPoint
import com.example.service.HeatmapInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorPlanGeometryTest {
    @Test fun calibrationUsesMeasuredReferenceAndImageAspect() {
        val original = FloorPlanDocument(imageAspect = 2f)
        val calculated = PlanGeometry.calibrate(original,
            NormalizedPoint(0f, 0f), NormalizedPoint(.5f, 0f), 4f)
        assertNotNull(calculated)
        assertEquals(8f, calculated!!.widthMeters, .001f)
        assertEquals(4f, calculated.heightMeters, .001f)
        assertTrue(calculated.scaleVerified)
        assertNull(PlanGeometry.calibrate(original, NormalizedPoint(.2f, .2f),
            NormalizedPoint(.2f, .2f), 4f))
        assertNull(PlanGeometry.calibrate(original, NormalizedPoint(0f, 0f),
            NormalizedPoint(1f, 0f), 0f))
    }

    @Test fun polygonAndGridSnappingExcludeEmptyOutsideSpace() {
        val lShape = listOf(
            NormalizedPoint(.1f,.1f), NormalizedPoint(.9f,.1f),
            NormalizedPoint(.9f,.4f), NormalizedPoint(.4f,.4f),
            NormalizedPoint(.4f,.9f), NormalizedPoint(.1f,.9f)
        )
        assertTrue(PlanGeometry.pointInOutline(NormalizedPoint(.2f,.8f), lShape))
        assertTrue(PlanGeometry.pointInOutline(NormalizedPoint(.8f,.2f), lShape))
        assertFalse(PlanGeometry.pointInOutline(NormalizedPoint(.8f,.8f), lShape))
        assertEquals(.5f, PlanGeometry.snapped(NormalizedPoint(.49f, .51f)).x, .001f)
    }

    @Test fun customHeatmapRequiresCalibratedRealSamplesAndStaysInsideOutline() {
        val doc = FloorPlanDocument(
            widthMeters = 8f, heightMeters = 8f, imageAspect = 1f,
            scaleVerified = true, outlineClosed = true,
            outline = listOf(
                NormalizedPoint(0f,0f), NormalizedPoint(.5f,0f),
                NormalizedPoint(.5f,1f), NormalizedPoint(0f,1f)
            )
        )
        val points = listOf(
            SurveyPoint(1, FloorPlanDocument.LAYOUT_ID, NormalizedPoint(.1f,.2f),
                "Room", "Real", -45, 5240, 48),
            SurveyPoint(2, FloorPlanDocument.LAYOUT_ID, NormalizedPoint(.3f,.5f),
                "Room", "Real", -57, 5240, 48),
            SurveyPoint(3, FloorPlanDocument.LAYOUT_ID, NormalizedPoint(.2f,.8f),
                "Room", "Real", -68, 5240, 48)
        )
        val blank = HeatmapInterpolator.computeHeatmapGrid(doc.toLayout(), points.take(2),
            doc.router, 12, 12)
        assertTrue(blank.isEmpty())
        val uncalibrated = HeatmapInterpolator.computeHeatmapGrid(
            doc.copy(scaleVerified = false).toLayout(), points, doc.router, 12, 12)
        assertTrue(uncalibrated.isEmpty())
        val measured = HeatmapInterpolator.computeHeatmapGrid(doc.toLayout(), points,
            doc.router, 12, 12)
        assertTrue(measured.isNotEmpty())
        assertTrue(measured.size < 12 * 12)
        assertTrue(measured.all { it.xNorm < .5f })
    }
}
