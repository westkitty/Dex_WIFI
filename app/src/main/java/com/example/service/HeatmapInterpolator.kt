package com.example.service

import androidx.compose.ui.graphics.Color
import com.example.model.ApartmentLayout
import com.example.model.HeatmapCell
import com.example.model.NormalizedPoint
import com.example.model.SurveyPoint
import com.example.ui.theme.SignalCritical
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalFair
import com.example.ui.theme.SignalGood
import com.example.ui.theme.SignalWeak
import kotlin.math.hypot
import kotlin.math.pow

object HeatmapInterpolator {

    /**
     * Computes a grid of interpolated signal values across the apartment layout.
     * Grid resolution: default 32 x 24 cells for smooth rendering performance on Canvas.
     */
    fun computeHeatmapGrid(
        layout: ApartmentLayout,
        surveyPoints: List<SurveyPoint>,
        routerPosition: NormalizedPoint?,
        gridCols: Int = 36,
        gridRows: Int = 28
    ): List<HeatmapCell> {
        val cells = ArrayList<HeatmapCell>(gridCols * gridRows)

        for (row in 0 until gridRows) {
            val yNorm = (row + 0.5f) / gridRows
            for (col in 0 until gridCols) {
                val xNorm = (col + 0.5f) / gridCols

                val estimatedRssi = if (surveyPoints.isNotEmpty()) {
                    interpolateFromSurvey(xNorm, yNorm, surveyPoints, routerPosition)
                } else if (routerPosition != null) {
                    estimateFromRouter(xNorm, yNorm, routerPosition, layout)
                } else {
                    -70f
                }

                cells.add(HeatmapCell(xNorm, yNorm, estimatedRssi))
            }
        }
        return cells
    }

    private fun interpolateFromSurvey(
        x: Float,
        y: Float,
        points: List<SurveyPoint>,
        router: NormalizedPoint?
    ): Float {
        var weightedSum = 0.0
        var totalWeight = 0.0

        for (pt in points) {
            val d = hypot(x - pt.position.x, y - pt.position.y).toDouble()
            if (d < 0.02) {
                return pt.rssi.toFloat()
            }
            // IDW weighting with power 2.5
            val w = 1.0 / d.pow(2.2)
            weightedSum += w * pt.rssi
            totalWeight += w
        }

        // Also factor router if available with anchor weight
        if (router != null) {
            val dRouter = hypot(x - router.x, y - router.y).toDouble()
            val wRouter = 1.0 / (dRouter + 0.08).pow(1.8) * 0.4
            weightedSum += wRouter * -35.0
            totalWeight += wRouter
        }

        return if (totalWeight > 0) {
            (weightedSum / totalWeight).toFloat().coerceIn(-95f, -30f)
        } else {
            -70f
        }
    }

    private fun estimateFromRouter(
        x: Float,
        y: Float,
        router: NormalizedPoint,
        layout: ApartmentLayout
    ): Float {
        // Physical distance in meters
        val dxMeters = (x - router.x) * layout.widthMeters
        val dyMeters = (y - router.y) * layout.heightMeters
        val distMeters = hypot(dxMeters, dyMeters).coerceAtLeast(0.8f)

        // Path loss exponent n = 2.8 for indoor apartment with drywall
        val rssiAt1m = -38f
        val pathLoss = 10f * 2.8f * kotlin.math.log10(distMeters.toDouble()).toFloat()

        // Count approximate wall crossings between rooms
        val routerRoom = layout.rooms.find {
            router.x in it.leftNorm..(it.leftNorm + it.widthNorm) &&
                    router.y in it.topNorm..(it.topNorm + it.heightNorm)
        }
        val targetRoom = layout.rooms.find {
            x in it.leftNorm..(it.leftNorm + it.widthNorm) &&
                    y in it.topNorm..(it.topNorm + it.heightNorm)
        }
        val wallPenalty = if (routerRoom != null && targetRoom != null && routerRoom.id != targetRoom.id) {
            6.5f // ~6.5 dB loss per interior drywall partition
        } else {
            0f
        }

        return (rssiAt1m - pathLoss - wallPenalty).coerceIn(-95f, -30f)
    }

    fun getColorForRssi(rssi: Float): Color {
        return when {
            rssi >= -50f -> SignalExcellent
            rssi >= -65f -> SignalGood
            rssi >= -75f -> SignalFair
            rssi >= -82f -> SignalWeak
            else -> SignalCritical
        }
    }
}
