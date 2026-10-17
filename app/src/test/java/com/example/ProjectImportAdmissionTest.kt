package com.example

import com.example.backup.ImportAdmission
import org.junit.Assert.*
import org.junit.Test

class ProjectImportAdmissionTest {
    @Test fun refusesInsufficientFreeSpace() {
        assertFalse(ImportAdmission.hasSpace(300L * 1024 * 1024, 120L * 1024 * 1024))
    }
    @Test fun acceptsOnlyTwiceFootprintPlusReserve() {
        val mb = 1024L * 1024
        assertFalse(ImportAdmission.hasSpace(339 * mb, 120 * mb))
        assertTrue(ImportAdmission.hasSpace(340 * mb, 120 * mb))
    }
    @Test fun rejectsNegativeAndOverflow() {
        assertFalse(ImportAdmission.hasSpace(-1, 1))
        assertFalse(ImportAdmission.hasSpace(Long.MAX_VALUE, Long.MAX_VALUE))
    }
}
