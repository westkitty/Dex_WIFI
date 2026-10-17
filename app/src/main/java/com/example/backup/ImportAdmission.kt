package com.example.backup

/** Fail-closed free-space gate for temporary, non-destructive archive staging. */
object ImportAdmission {
    private const val RESERVE_BYTES = 100L * 1024L * 1024L

    fun hasSpace(freeBytes: Long, temporaryFootprintBytes: Long): Boolean {
        if (freeBytes < 0 || temporaryFootprintBytes < 0) return false
        if (temporaryFootprintBytes > (Long.MAX_VALUE - RESERVE_BYTES) / 2) return false
        return freeBytes >= 2 * temporaryFootprintBytes + RESERVE_BYTES
    }
}
