package com.example.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class VerifiedProjectArchive(
    val planJson: String?,
    val surveysJson: String,
    val photos: List<Pair<String, ByteArray>>
)

/**
 * Portable project ZIP v1. Validates every entry before any caller may use it.
 * Does not mutate application state or access the network.
 */
object ProjectArchiveCodec {
    private const val MAX_ENTRY = 32 * 1024 * 1024
    private const val MAX_TOTAL = 128 * 1024 * 1024
    private const val MAX_ENTRIES = 100
    private val PHOTO_NAME = Regex("plan_photo_[A-Za-z0-9_-]+\\.jpg")

    fun write(
        output: OutputStream,
        rawPlanJson: String?,
        surveysJson: String,
        photos: List<Pair<String, ByteArray>>
    ) {
        require(photos.size <= MAX_ENTRIES - 3)
        val payload = linkedMapOf<String, ByteArray>()
        if (rawPlanJson != null) payload["plan.json"] = rawPlanJson.toByteArray(Charsets.UTF_8)
        payload["surveys.json"] = surveysJson.toByteArray(Charsets.UTF_8)
        for ((name, bytes) in photos) {
            require(PHOTO_NAME.matches(name))
            require(payload.put("photos/$name", bytes) == null)
        }
        require(payload.values.sumOf { it.size.toLong() } <= MAX_TOTAL)
        require(payload.values.all { it.size <= MAX_ENTRY })
        val hashes = JSONObject()
        payload.forEach { (name, bytes) -> hashes.put(name, sha256(bytes)) }
        val manifest = JSONObject().put("version", 1).put("hashes", hashes)
            .put("entryCount", payload.size).toString().toByteArray(Charsets.UTF_8)
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in payload + ("manifest.json" to manifest)) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.finish()
        }
    }

    fun verify(input: InputStream): VerifiedProjectArchive {
        val entries = linkedMapOf<String, ByteArray>()
        val normalized = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!entry.isDirectory && name.isNotBlank())
                require(!name.startsWith("/") && !name.contains('\\') &&
                    name.split('/').none { it == ".." || it == "." || it.isBlank() })
                require(normalized.add(name.lowercase())) { "Duplicate archive path" }
                require(entries.size < MAX_ENTRIES)
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var entryTotal = 0L
                while (true) {
                    val read = zip.read(buffer)
                    if (read == -1) break
                    entryTotal += read
                    total += read
                    require(entryTotal <= MAX_ENTRY && total <= MAX_TOTAL)
                    out.write(buffer, 0, read)
                }
                entries[name] = out.toByteArray()
                zip.closeEntry()
            }
        }
        val manifestBytes = entries.remove("manifest.json") ?: error("Missing manifest")
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        require(manifest.getInt("version") == 1)
        val hashes = manifest.getJSONObject("hashes")
        require(manifest.getInt("entryCount") == entries.size)
        require(hashes.length() == entries.size)
        for ((name, bytes) in entries) {
            require(hashes.has(name) && hashes.getString(name) == sha256(bytes)) {
                "Archive integrity mismatch"
            }
            require(name == "plan.json" || name == "surveys.json" ||
                (name.startsWith("photos/") && PHOTO_NAME.matches(name.removePrefix("photos/"))))
        }
        require(entries.containsKey("surveys.json"))
        val surveys = String(entries.getValue("surveys.json"), Charsets.UTF_8)
        require(JSONArray(surveys).length() <= 50_000)
        return VerifiedProjectArchive(
            entries["plan.json"]?.toString(Charsets.UTF_8),
            surveys,
            entries.filterKeys { it.startsWith("photos/") }
                .map { it.key.removePrefix("photos/") to it.value }
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
