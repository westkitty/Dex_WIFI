package com.example

import com.example.backup.ProjectImportVerifier
import com.example.backup.ProjectArchiveCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectImportVerifierTest {
    @Test fun validArchiveCanBeInspectedWithoutRestoring() {
        val out = ByteArrayOutputStream()
        ProjectArchiveCodec.write(out, "{invalid-but-preserved}", "[]", emptyList())
        val result = ProjectImportVerifier.inspect(ByteArrayInputStream(out.toByteArray()))
        assertEquals(0, result.rowCount)
        assertEquals(0, result.photoCount)
        assertEquals("RAW_UNVERIFIED", result.planStatus)
    }

    @Test fun malformedArchiveIsRejectedWithoutPromotion() {
        assertThrows(Exception::class.java) {
            ProjectImportVerifier.inspect(ByteArrayInputStream("not a ZIP".toByteArray()))
        }
    }
}
