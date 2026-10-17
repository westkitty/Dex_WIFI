package com.example

import com.example.model.NormalizedPoint
import com.example.model.PlanSegment
import com.example.model.SketchStrokeBatch
import com.example.ui.components.FloorPlanPhotoPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class PerformanceUpliftTest {
    @Test
    fun importedPhotoDecodingHasBoundedResolution() {
        assertEquals(1, FloorPlanPhotoPolicy.sampleSize(900, 600))
        assertEquals(2, FloorPlanPhotoPolicy.sampleSize(2048, 1536))
        assertEquals(4, FloorPlanPhotoPolicy.sampleSize(4096, 3072))
        assertEquals(1, FloorPlanPhotoPolicy.sampleSize(0, 0))
    }

    @Test
    fun batchedSketchIsPixelDataEquivalentToRepeatedAppends() {
        val segment = PlanSegment(NormalizedPoint(.1f, .2f), NormalizedPoint(.2f, .3f))
        val existing = List(1500) { segment }
        val pending = List(300) { segment }
        fun oldPath(): List<PlanSegment> {
            var result: List<PlanSegment> = existing
            for (part in pending) result = result + part
            return result
        }
        fun newPath(): List<PlanSegment> = SketchStrokeBatch.append(existing, pending)
        assertEquals(oldPath(), newPath())
        assertSame(existing, SketchStrokeBatch.append(existing, emptyList()))
        assertEquals(2500, SketchStrokeBatch.append(existing, List(1200) { segment }).size)

        // Matched JVM microbenchmark of ONLY the list-copy path. Not frame-time,
        // end-to-end stylus latency, or measured battery use. No flaky speed assertion.
        repeat(6) { oldPath(); newPath() }
        val before = LongArray(17) { measureNanoTime { oldPath() } }.sorted()
        val after = LongArray(17) { measureNanoTime { newPath() } }.sorted()
        println("PERF_SKETCH_LIST_COPY_MEDIAN_NS baseline=${before[8]} batched=${after[8]}")
        assertTrue(before[8] > 0L && after[8] > 0L)
    }
}
