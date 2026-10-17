package com.example.backup

import com.example.data.SurveyEntity

/**
 * Lossless legacy snapshot. Never substitute decoded defaults for raw plan bytes.
 * Photos are copied into memory and bounded by the reader's admission limits.
 */
data class LegacySnapshot(
    val rawPlanJson: String?,
    val surveys: List<SurveyEntity>,
    val photos: List<LegacyPhoto>,
    val capturedAtMillis: Long
)

data class LegacyPhoto(val filename: String, val bytes: ByteArray)
