package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FloorPlanStore
import com.example.model.FloorPlanDocument
import com.example.model.NormalizedPoint
import com.example.model.PlanElementType
import com.example.model.PlanSegment
import com.example.model.RoomType
import com.example.model.RoomZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloorPlanStoreTest {
    @Test fun roundTripPrivateCustomFloorPlan() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = FloorPlanStore(ctx)
        val original = FloorPlanDocument(
            name = "Measured apartment",
            widthMeters = 11.3f,
            heightMeters = 7.8f,
            scaleVerified = true,
            outlineClosed = true,
            outline = listOf(NormalizedPoint(.1f,.1f), NormalizedPoint(.9f,.1f),
                NormalizedPoint(.9f,.9f), NormalizedPoint(.1f,.9f)),
            segments = listOf(PlanSegment(NormalizedPoint(.4f,.1f),
                NormalizedPoint(.4f,.8f), PlanElementType.DOOR)),
            rooms = listOf(RoomZone("custom-one", "My studio", .1f,.1f,.7f,.6f,
                RoomType.LIVING_ROOM)),
            router = NormalizedPoint(.25f,.33f)
        )
        assertTrue(store.save(original))
        val restored = store.load()
        assertNotNull(restored)
        assertEquals(original.name, restored!!.name)
        assertEquals(original.widthMeters, restored.widthMeters, .0001f)
        assertEquals(original.heightMeters, restored.heightMeters, .0001f)
        assertTrue(restored.scaleVerified)
        assertEquals(original.outline, restored.outline)
        assertEquals(original.segments, restored.segments)
        assertEquals(original.rooms, restored.rooms)
        assertEquals(original.router, restored.router)
        assertNull(store.photoFile("../../shared-secrets.jpg"))
    }
}
