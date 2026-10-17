package com.example.backup

import android.content.Context
import android.net.Uri
import com.example.data.SurveyDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

sealed interface ProjectExportResult {
    data class Verified(val surveyRows: Int, val photoCount: Int) : ProjectExportResult
    data class Failed(val explanation: String) : ProjectExportResult
    data class Unverified(val explanation: String) : ProjectExportResult
}

/** User-selected SAF destination. Never writes to or clears live project state. */
class ProjectExportCoordinator(
    private val context: Context,
    private val dao: SurveyDao
) {
    suspend fun export(uri: Uri): ProjectExportResult = withContext(Dispatchers.IO) {
        try {
            val snapshot = LegacySnapshotReader(context, dao).capture()
            val rows = JSONArray()
            snapshot.surveys.forEach { item ->
                rows.put(JSONObject().apply {
                    put("id", item.id)
                    put("layoutId", item.layoutId)
                    put("posX", item.posX.toDouble())
                    put("posY", item.posY.toDouble())
                    put("roomName", item.roomName)
                    put("primarySsid", item.primarySsid)
                    put("rssi", item.rssi)
                    put("frequency", item.frequency)
                    put("channel", item.channel)
                    put("timestamp", item.timestamp)
                    put("notes", item.notes)
                    put("provenance", "LEGACY_UNVERIFIED")
                })
            }
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Document provider denied writing")
            output.use {
                ProjectArchiveCodec.write(
                    it, snapshot.rawPlanJson, rows.toString(),
                    snapshot.photos.map { photo -> photo.filename to photo.bytes }
                )
            }
            val check = try {
                context.contentResolver.openInputStream(uri)?.use(ProjectArchiveCodec::verify)
            } catch (_: Exception) { null }
            if (check == null) {
                ProjectExportResult.Unverified("File was written but destination readback was unavailable. Do not treat as a verified backup.")
            } else if (check.surveysJson != rows.toString() ||
                check.planJson != snapshot.rawPlanJson ||
                check.photos.size != snapshot.photos.size
            ) {
                ProjectExportResult.Failed("Archive readback did not match the source snapshot.")
            } else {
                ProjectExportResult.Verified(snapshot.surveys.size, snapshot.photos.size)
            }
        } catch (error: Exception) {
            ProjectExportResult.Failed(error.message ?: "Could not export the project")
        }
    }
}
