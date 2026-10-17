package com.example.backup

import android.content.Context
import com.example.data.SurveyDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Reads every layout from Room and the original preference JSON without calling
 * FloorPlanStore.load(), whose legacy error path silently returns null.
 *
 * Never writes to Room, preferences or photo files.
 */
class LegacySnapshotReader(
    private val context: Context,
    private val surveyDao: SurveyDao
) {
    suspend fun capture(): LegacySnapshot = withContext(Dispatchers.IO) {
        val surveys = surveyDao.snapshotAllPoints()
        val prefs = context.getSharedPreferences("floorplan_studio_v1", Context.MODE_PRIVATE)
        val raw = prefs.getString("document", null)
        if (raw != null && raw.length > MAX_PLAN_CHARS) {
            throw IOException("Raw floor-plan exceeds snapshot admission budget")
        }
        val photos = context.filesDir.listFiles()
            ?.filter { it.isFile && PHOTO_NAME.matches(it.name) }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (photos.size > MAX_PHOTOS) throw IOException("Too many private plan photos")
        var bytesTotal = 0L
        val captured = photos.map { file ->
            val size = file.length()
            if (size < 0 || size > MAX_PHOTO_BYTES ||
                bytesTotal > MAX_TOTAL_PHOTO_BYTES - size
            ) throw IOException("Plan photos exceed snapshot budget")
            bytesTotal += size
            val bytes = file.readBytes()
            if (bytes.size.toLong() != size) throw IOException("Photo changed during snapshot")
            LegacyPhoto(file.name, bytes)
        }
        LegacySnapshot(raw, surveys, captured, System.currentTimeMillis())
    }

    companion object {
        private val PHOTO_NAME = Regex("plan_photo_[A-Za-z0-9_\\-]+\\.jpg")
        const val MAX_PLAN_CHARS = 4_000_000
        const val MAX_PHOTOS = 256
        const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
        const val MAX_TOTAL_PHOTO_BYTES = 128L * 1024 * 1024
    }
}
