package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps imported blueprint decoding away from the UI thread and bounds bitmap pixels. */
internal object FloorPlanPhotoPolicy {
    fun sampleSize(width: Int, height: Int, maxDimension: Int = 1536): Int {
        if (width <= 0 || height <= 0 || maxDimension < 1) return 1
        var sample = 1
        while (maxOf(width / sample, height / sample) > maxDimension && sample < (1 shl 29)) {
            sample *= 2
        }
        return sample
    }
}

@Composable
internal fun rememberFloorPlanPhoto(file: File?): ImageBitmap? {
    val path = file?.absolutePath
    val modified = file?.lastModified() ?: -1L
    val photo by produceState<ImageBitmap?>(initialValue = null, path, modified) {
        value = null
        value = withContext(Dispatchers.IO) {
            if (path == null) null else runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth < 1 || bounds.outHeight < 1) return@runCatching null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = FloorPlanPhotoPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
                }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return photo
}
