package com.example

import com.example.backup.ProjectArchiveCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectArchiveCodecTest {
    @Test fun archiveRoundTripPreservesRawPlanAndRows() {
        val output = ByteArrayOutputStream()
        ProjectArchiveCodec.write(
            output, "{not-json-but-important}", "[{\"id\":17,\"layoutId\":\"preset\"}]",
            listOf("plan_photo_123_1.jpg" to byteArrayOf(1, 2, 3))
        )
        val verified = ProjectArchiveCodec.verify(ByteArrayInputStream(output.toByteArray()))
        assertEquals("{not-json-but-important}", verified.planJson)
        assertTrue(verified.surveysJson.contains("preset"))
        assertArrayEquals(byteArrayOf(1, 2, 3), verified.photos.single().second)
    }

    @Test fun tamperedArchiveRejected() {
        val output = ByteArrayOutputStream()
        ProjectArchiveCodec.write(output, "{}", "[]", emptyList())
        // Repack a structurally valid ZIP with altered survey content but the
        // ORIGINAL manifest. The integrity check must reject the changed hash.
        val modified = ByteArrayOutputStream()
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { source ->
            ZipOutputStream(modified).use { target ->
                while (true) {
                    val entry = source.nextEntry ?: break
                    val bytes = source.readBytes()
                    target.putNextEntry(ZipEntry(entry.name))
                    target.write(if (entry.name == "surveys.json") "[{}]".toByteArray() else bytes)
                    target.closeEntry()
                    source.closeEntry()
                }
            }
        }
        assertThrows(Exception::class.java) {
            ProjectArchiveCodec.verify(ByteArrayInputStream(modified.toByteArray()))
        }
    }

    @Test fun traversalEntryRejected() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("../private.txt"))
            zip.write("unsafe".toByteArray())
            zip.closeEntry()
        }
        assertThrows(Exception::class.java) {
            ProjectArchiveCodec.verify(ByteArrayInputStream(output.toByteArray()))
        }
    }

    @Test fun duplicateEntryRejected() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("plan.json")); zip.write("{}".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("PLAN.JSON")); zip.write("{}".toByteArray()); zip.closeEntry()
        }
        assertThrows(Exception::class.java) {
            ProjectArchiveCodec.verify(ByteArrayInputStream(output.toByteArray()))
        }
    }
}
