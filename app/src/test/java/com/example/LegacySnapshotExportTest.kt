package com.example

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
class LegacySnapshotExportTest {
    @Test fun invalidRawPlanIsPreservedWithoutInterpretation() {
        val stream = ByteArrayOutputStream()
        ProjectArchiveCodec.write(stream, "{bad json", "[]", emptyList())
        assertEquals("{bad json", ProjectArchiveCodec.verify(
            ByteArrayInputStream(stream.toByteArray())).planJson)
    }

    @Test fun noPlanDoesNotRemoveSurveys() {
        val stream = ByteArrayOutputStream()
        val rows = "[{\"id\":9,\"layoutId\":\"demo\"}]"
        ProjectArchiveCodec.write(stream, null, rows, emptyList())
        val result = ProjectArchiveCodec.verify(ByteArrayInputStream(stream.toByteArray()))
        assertNull(result.planJson)
        assertEquals(rows, result.surveysJson)
    }
}
