package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.WifiScannerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Protect against constructor-order crashes when a worker on Dispatchers.Default
 * runs before an initialized simulated-network field is ready.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WifiScannerStartupTest {
    @Test
    fun simulationListIsInitializedBeforeBackgroundPollingStarts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val scanner = WifiScannerService(context, scope)
        try {
            scanner.setSimulatedMode(true)
            val observed = withTimeout(10_000L) {
                scanner.networks.first { it.isNotEmpty() }
            }
            assertTrue("Simulation should yield initialized neighbors", observed.isNotEmpty())
            assertTrue("Simulation should identify its own mode", scanner.isSimulatedMode.value)
        } finally {
            scanner.cleanup()
            scope.cancel()
        }
    }
}
