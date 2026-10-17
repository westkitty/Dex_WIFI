package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FloorPlanStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Backups must preserve the exact source bytes, including invalid JSON. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RawPlanSnapshotTest {
    @Test fun missingDocumentIsNotInvented() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("floorplan_studio_v1", Context.MODE_PRIVATE)
            .edit().remove("document").commit()
        assertNull(FloorPlanStore(ctx).rawDocumentForBackup())
    }

    @Test fun malformedDocumentIsPreservedByteForByte() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val source = "{broken-json\\n\"opaque\":true"
        ctx.getSharedPreferences("floorplan_studio_v1", Context.MODE_PRIVATE)
            .edit().putString("document", source).commit()
        assertEquals(source, FloorPlanStore(ctx).rawDocumentForBackup())
    }
}
