package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.WifiScannerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Constructor startup must never launch a polling coroutine or leak a worker.
 * Foreground activation uses Unconfined to exercise the first sample synchronously.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WifiScannerStartupTest {
    @Test
    fun constructorIsInertUntilExplicitForegroundStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scanner = WifiScannerService(context, scope, Dispatchers.Unconfined)
        try {
            assertTrue("Without location permission, startup must clearly show DEMO mode",
                scanner.isSimulatedMode.value)
            assertTrue("Constructors must not launch scanner workers", scanner.networks.value.isEmpty())
            scanner.startForegroundScanning()
            assertTrue("Foreground poll must see initialized simulated neighbors",
                scanner.networks.value.isNotEmpty())
            scanner.stopForegroundScanning()
            assertNull("Demo connected radio must never be accepted as a real survey",
                scanner.realConnectedSnapshotOrNull())
        } finally {
            scanner.cleanup()
            scope.cancel()
        }
    }

    @Test
    fun cleanupCancelsDelayedScanAndPreventsPostDestroyUpdates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scanner = WifiScannerService(context, scope, Dispatchers.Unconfined)
        try {
            scanner.startForegroundScanning()
            scanner.setSimulatedMode(true) // queue a delayed demo publication
            scanner.cleanup()
            val snapshot = scanner.networks.value
            val mode = scanner.isSimulatedMode.value
            scanner.triggerScan()
            scanner.setSimulatedMode(false)
            delay(450)
            assertEquals("No late demo publication after cleanup", snapshot, scanner.networks.value)
            assertEquals("Disposed scanner must not change source", mode, scanner.isSimulatedMode.value)
            assertNull(scanner.realConnectedSnapshotOrNull())
        } finally {
            scanner.cleanup()
            scope.cancel()
        }
    }

    @Test
    fun backgroundStopRejectsPendingDemoUpdatesButResumeStillWorks() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scanner = WifiScannerService(ctx, scope, Dispatchers.Unconfined)
        try {
            scanner.startForegroundScanning()
            scanner.setSimulatedMode(true)
            scanner.stopForegroundScanning()
            val stoppedState = scanner.networks.value
            delay(420)
            assertEquals("No delayed publication while backgrounded", stoppedState, scanner.networks.value)
            assertNull(scanner.realConnectedSnapshotOrNull())
            scanner.startForegroundScanning()
            assertTrue("Resume starts simulation without recreating service", scanner.networks.value.isNotEmpty())
        } finally {
            scanner.cleanup()
            scope.cancel()
        }
    }
}
