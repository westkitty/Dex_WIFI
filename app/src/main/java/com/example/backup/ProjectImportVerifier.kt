package com.example.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/** Verified preview only. This API has no live-database or preference write path. */
data class ImportPreview(
    val rowCount: Int,
    val photoCount: Int,
    val planStatus: String
)

object ProjectImportVerifier {
    fun inspect(source: InputStream): ImportPreview {
        val verified = ProjectArchiveCodec.verify(source)
        val rows = JSONArray(verified.surveysJson)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            require(row.has("id") && row.has("layoutId")) { "Missing legacy row identity" }
            require(row.getLong("id") >= 0)
            require(row.getString("layoutId").isNotBlank())
            for (key in listOf("posX", "posY")) {
                require(row.has(key) && row.getDouble(key).isFinite()) { "Invalid survey coordinate" }
            }
        }
        val status = when (val raw = verified.planJson) {
            null -> "ABSENT"
            else -> try {
                JSONObject(raw)
                "RAW_UNVERIFIED"
            } catch (_: Exception) {
                "RAW_UNVERIFIED"
            }
        }
        return ImportPreview(rows.length(), verified.photos.size, status)
    }
}
