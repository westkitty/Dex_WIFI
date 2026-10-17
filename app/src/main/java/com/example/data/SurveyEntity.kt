package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.NormalizedPoint
import com.example.model.SurveyPoint

@Entity(tableName = "survey_points")
data class SurveyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val layoutId: String,
    val posX: Float,
    val posY: Float,
    val roomName: String,
    val primarySsid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val timestamp: Long,
    val notes: String
) {
    fun toDomain(): SurveyPoint = SurveyPoint(
        id = id,
        layoutId = layoutId,
        position = NormalizedPoint(posX, posY),
        roomName = roomName,
        primarySsid = primarySsid,
        rssi = rssi,
        frequency = frequency,
        channel = channel,
        timestamp = timestamp,
        notes = notes
    )

    companion object {
        fun fromDomain(point: SurveyPoint): SurveyEntity = SurveyEntity(
            id = point.id,
            layoutId = point.layoutId,
            posX = point.position.x,
            posY = point.position.y,
            roomName = point.roomName,
            primarySsid = point.primarySsid,
            rssi = point.rssi,
            frequency = point.frequency,
            channel = point.channel,
            timestamp = point.timestamp,
            notes = point.notes
        )
    }
}
