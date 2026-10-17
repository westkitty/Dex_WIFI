package com.example.service

/** Android ScanResult.timestamp is MICROSECONDS since boot, not Unix time. */
object ScanFreshness {
    // Conservative ceiling: don't assert that a scan older than a day is current.
    private const val MAX_AGE_MILLIS = 86_400_000L

    fun ageMillis(elapsedRealtimeMillis: Long, scanTimestampMicros: Long): Long? {
        if (elapsedRealtimeMillis < 0L || scanTimestampMicros <= 0L) return null
        val observedSinceBootMillis = scanTimestampMicros / 1_000L
        val age = elapsedRealtimeMillis - observedSinceBootMillis
        return age.takeIf { it in 0L..MAX_AGE_MILLIS }
    }

    fun wallClockMillis(
        currentWallClockMillis: Long,
        elapsedRealtimeMillis: Long,
        scanTimestampMicros: Long
    ): Long? = ageMillis(elapsedRealtimeMillis, scanTimestampMicros)
        ?.let { age -> (currentWallClockMillis - age).takeIf { it > 0L } }
}
