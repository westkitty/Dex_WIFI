package com.example.model

import kotlin.math.hypot

/** User-authored geometry. Coordinates are fractions of the canvas, never screen pixels. */
enum class PlanElementType { WALL, DOOR, WINDOW }

data class PlanSegment(
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val kind: PlanElementType = PlanElementType.WALL
)

data class FloorPlanDocument(
    val name: String = "My apartment",
    val widthMeters: Float = 12f,
    val heightMeters: Float = 9f,
    val scaleVerified: Boolean = false,
    val photoFile: String? = null,
    val imageAspect: Float = 4f / 3f,
    val outline: List<NormalizedPoint> = emptyList(),
    val outlineClosed: Boolean = false,
    val segments: List<PlanSegment> = emptyList(),
    val rooms: List<RoomZone> = emptyList(),
    val router: NormalizedPoint = NormalizedPoint(.5f, .5f),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toLayout(): ApartmentLayout = ApartmentLayout(
        id = LAYOUT_ID,
        name = name,
        description = "User-drawn floor plan. Only measured readings count as surveys.",
        widthMeters = widthMeters,
        heightMeters = heightMeters,
        rooms = rooms,
        defaultRouterPosition = router,
        outline = if (outlineClosed) outline else emptyList(),
        referenceCalibrated = scaleVerified
    )

    companion object {
        const val LAYOUT_ID = "user_floorplan_v1"
    }
}

/** Pure geometry shared by touch editor, rendering, and verification tests. */
object PlanGeometry {
    fun clamp(p: NormalizedPoint): NormalizedPoint =
        NormalizedPoint(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))

    fun snapped(p: NormalizedPoint, step: Float = .025f): NormalizedPoint {
        if (step <= 0f) return clamp(p)
        return clamp(NormalizedPoint(
            (kotlin.math.round(p.x / step) * step),
            (kotlin.math.round(p.y / step) * step)
        ))
    }

    fun pointInOutline(p: NormalizedPoint, outline: List<NormalizedPoint>): Boolean {
        if (outline.size < 3) return true
        var inside = false
        var j = outline.lastIndex
        for (i in outline.indices) {
            val a = outline[i]
            val b = outline[j]
            if ((a.y > p.y) != (b.y > p.y)) {
                val xCross = (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
                if (p.x < xCross) inside = !inside
            }
            j = i
        }
        return inside
    }

    fun distanceMeters(a: NormalizedPoint, b: NormalizedPoint, widthMeters: Float, heightMeters: Float): Float =
        hypot((a.x - b.x) * widthMeters, (a.y - b.y) * heightMeters)

    /** Calibrates an undistorted photo from one real tape-measured reference distance. */
    fun calibrate(
        doc: FloorPlanDocument,
        a: NormalizedPoint,
        b: NormalizedPoint,
        measuredMeters: Float
    ): FloorPlanDocument? {
        if (!measuredMeters.isFinite() || measuredMeters !in .1f..200f) return null
        val ratio = doc.imageAspect
        if (!ratio.isFinite() || ratio !in .2f..8f) return null
        val unitDistance = hypot(a.x - b.x, (a.y - b.y) / ratio)
        if (unitDistance < .03f) return null
        val width = measuredMeters / unitDistance
        val height = width / ratio
        if (width !in .5f..500f || height !in .5f..500f) return null
        return doc.copy(widthMeters = width, heightMeters = height, scaleVerified = true)
    }

    fun roomAt(doc: FloorPlanDocument, point: NormalizedPoint): String =
        doc.rooms.lastOrNull { r ->
            point.x in r.leftNorm..(r.leftNorm + r.widthNorm) &&
                point.y in r.topNorm..(r.topNorm + r.heightNorm)
        }?.name ?: "Unlabelled area"
}
