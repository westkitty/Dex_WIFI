package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.model.FloorPlanDocument
import com.example.model.NormalizedPoint
import com.example.model.PlanElementType
import com.example.model.PlanSegment
import com.example.model.RoomType
import com.example.model.RoomZone
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.max

/**
 * Small, private, versioned JSON plan. Existing Room survey DB is never migrated or cleared.
 * Camera and gallery content is copied into private storage; no cloud upload.
 */
class FloorPlanStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("floorplan_studio_v1", Context.MODE_PRIVATE)

    /** Exact stored document for archival, including malformed or future-version JSON.
     * Never parse, normalize or rewrite the source during a read-only snapshot.
     */
    fun rawDocumentForBackup(): String? = prefs.getString("document", null)

    fun load(): FloorPlanDocument? {
        return try {
            val value = prefs.getString("document", null) ?: return null
            decode(JSONObject(value))
        } catch (_: Exception) {
            null
        }
    }

    fun save(document: FloorPlanDocument): Boolean {
        if (document.widthMeters <= 0f || document.heightMeters <= 0f) return false
        if (document.outline.size > 1500 || document.segments.size > 2500 || document.rooms.size > 200) return false
        return prefs.edit().putString("document", encode(document).toString()).commit()
    }

    /** Returns filename and actual oriented image aspect ratio; never retains external URI. */
    @Throws(IOException::class)
    fun importPhoto(uri: Uri): Pair<String, Float> {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val headerStream = resolver.openInputStream(uri) ?: throw IOException("Could not open photo")
        headerStream.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Not an image")
        var sample = 1
        while (max(options.outWidth / sample, options.outHeight / sample) > 2048) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Image decode failed")
        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            ) } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
        }
        val oriented = if (matrix.isIdentity) bitmap else try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) { bitmap }
        if (oriented !== bitmap) bitmap.recycle()
        val name = "plan_photo_${System.currentTimeMillis()}_${(0..9999).random()}.jpg"
        val file = File(context.filesDir, name)
        try {
            file.outputStream().use { output ->
                if (!oriented.compress(Bitmap.CompressFormat.JPEG, 86, output)) {
                    throw IOException("Could not encode imported photo")
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            oriented.recycle()
        }
        return name to (optionsAspect(file))
    }

    private fun optionsAspect(file: File): Float {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return (opts.outWidth.toFloat() / opts.outHeight.coerceAtLeast(1)).coerceIn(.2f, 8f)
    }

    fun photoFile(name: String?): File? {
        if (name == null || name != File(name).name || !name.startsWith("plan_photo_") ||
            !name.endsWith(".jpg")) return null
        return File(context.filesDir, name).takeIf { it.isFile }
    }

    private fun point(p: NormalizedPoint) =
        JSONArray().put(p.x.toDouble()).put(p.y.toDouble())
    private fun fromPoint(a: JSONArray) = NormalizedPoint(
        a.optDouble(0, 0.5).toFloat().coerceIn(0f, 1f),
        a.optDouble(1, 0.5).toFloat().coerceIn(0f, 1f)
    )

    private fun encode(doc: FloorPlanDocument) = JSONObject().apply {
        put("version", 1)
        put("name", doc.name)
        put("width", doc.widthMeters.toDouble())
        put("height", doc.heightMeters.toDouble())
        put("verified", doc.scaleVerified)
        put("photo", doc.photoFile ?: JSONObject.NULL)
        put("aspect", doc.imageAspect.toDouble())
        put("router", point(doc.router))
        put("closed", doc.outlineClosed)
        put("outline", JSONArray().apply { doc.outline.forEach { put(point(it)) } })
        put("segments", JSONArray().apply {
            doc.segments.forEach { s -> put(JSONObject().apply {
                put("a", point(s.start)); put("b", point(s.end)); put("kind", s.kind.name)
            }) }
        })
        put("rooms", JSONArray().apply {
            doc.rooms.forEach { r -> put(JSONObject().apply {
                put("id", r.id); put("name", r.name)
                put("x", r.leftNorm.toDouble()); put("y", r.topNorm.toDouble())
                put("w", r.widthNorm.toDouble()); put("h", r.heightNorm.toDouble())
                put("type", r.roomType.name)
            }) }
        })
        put("updatedAt", doc.updatedAt)
    }

    private fun decode(root: JSONObject): FloorPlanDocument {
        if (root.optInt("version") != 1) throw IOException("Unsupported floor-plan schema")
        fun array(key: String) = root.optJSONArray(key) ?: JSONArray()
        val outlineJson = array("outline")
        val segmentsJson = array("segments")
        val roomsJson = array("rooms")
        if (outlineJson.length() > 1500 || segmentsJson.length() > 2500 || roomsJson.length() > 200) {
            throw IOException("Plan exceeds storage limits")
        }
        val outline = (0 until outlineJson.length()).map { fromPoint(outlineJson.getJSONArray(it)) }
        val segments = (0 until segmentsJson.length()).map {
            val j = segmentsJson.getJSONObject(it)
            PlanSegment(
                fromPoint(j.getJSONArray("a")), fromPoint(j.getJSONArray("b")),
                runCatching { PlanElementType.valueOf(j.getString("kind")) }.getOrDefault(PlanElementType.WALL)
            )
        }
        val rooms = (0 until roomsJson.length()).map { index ->
            val r = roomsJson.getJSONObject(index)
            RoomZone(
                r.optString("id", "room_$index"),
                r.optString("name", "Room").take(60),
                r.optDouble("x", 0.0).toFloat().coerceIn(0f, 1f),
                r.optDouble("y", 0.0).toFloat().coerceIn(0f, 1f),
                r.optDouble("w", 0.2).toFloat().coerceIn(.01f, 1f),
                r.optDouble("h", 0.2).toFloat().coerceIn(.01f, 1f),
                runCatching { RoomType.valueOf(r.optString("type")) }.getOrDefault(RoomType.LIVING_ROOM)
            )
        }
        val aspect = root.optDouble("aspect", 4.0 / 3.0).toFloat()
        val width = root.optDouble("width", 12.0).toFloat()
        val height = root.optDouble("height", 9.0).toFloat()
        return FloorPlanDocument(
            name = root.optString("name", "My apartment").take(70),
            widthMeters = width.takeIf { it.isFinite() && it in .5f..500f } ?: 12f,
            heightMeters = height.takeIf { it.isFinite() && it in .5f..500f } ?: 9f,
            scaleVerified = root.optBoolean("verified", false),
            photoFile = root.optString("photo").takeIf { it.isNotBlank() && it != "null" &&
                it == File(it).name && it.startsWith("plan_photo_") },
            imageAspect = aspect.takeIf { it.isFinite() && it in .2f..8f } ?: (4f / 3f),
            outline = outline,
            outlineClosed = root.optBoolean("closed") && outline.size >= 3,
            segments = segments,
            rooms = rooms,
            router = root.optJSONArray("router")?.let(::fromPoint) ?: NormalizedPoint(.5f, .5f),
            updatedAt = root.optLong("updatedAt", 0L)
        )
    }
}
