package com.example.model

data class RoomZone(
    val id: String,
    val name: String,
    val leftNorm: Float,   // 0.0 to 1.0 (relative to apartment bounding box)
    val topNorm: Float,    // 0.0 to 1.0
    val widthNorm: Float,  // 0.0 to 1.0
    val heightNorm: Float, // 0.0 to 1.0
    val roomType: RoomType = RoomType.LIVING_ROOM
)

enum class RoomType {
    LIVING_ROOM,
    BEDROOM,
    KITCHEN,
    OFFICE,
    BATHROOM,
    HALLWAY,
    BALCONY
}

data class NormalizedPoint(
    val x: Float, // 0.0f to 1.0f
    val y: Float  // 0.0f to 1.0f
)

data class ApartmentLayout(
    val id: String,
    val name: String,
    val description: String,
    val widthMeters: Float = 12f,
    val heightMeters: Float = 9f,
    val rooms: List<RoomZone>,
    val defaultRouterPosition: NormalizedPoint = NormalizedPoint(0.25f, 0.45f)
)

data class SurveyPoint(
    val id: Long = 0,
    val layoutId: String,
    val position: NormalizedPoint,
    val roomName: String,
    val primarySsid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

data class HeatmapCell(
    val xNorm: Float,
    val yNorm: Float,
    val estimatedRssi: Float
)
