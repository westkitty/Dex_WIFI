package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.WifiNetwork
import com.example.service.ConnectionBaselineStore
import com.example.service.ConnectionDoctorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionDoctorTest {
    private val now = 1_800_000_000_000L
    private fun network(
        ssid: String = "Home",
        bssid: String = "aa:bb:cc:dd:ee:ff",
        frequency: Int = 2412,
        rssi: Int = -70,
        timestamp: Long = now - 2_000L,
        linkMbps: Int? = 150
    ) = WifiNetwork(ssid = ssid, bssid = bssid, frequency = frequency,
        rssi = rssi, isConnected = true, timestamp = timestamp, linkSpeedMbps = linkMbps)

    @Test fun simulationAndStaleInputsCannotCreateBaseline() {
        assertNull(ConnectionDoctorEngine.observeConnected(network(), true, now))
        assertNull(ConnectionDoctorEngine.observeConnected(network(timestamp = 0L), false, now))
        assertNull(ConnectionDoctorEngine.observeConnected(network(timestamp = now - 200_000L), false, now))
        assertNotNull(ConnectionDoctorEngine.observeConnected(network(), false, now))
    }

    @Test fun comparisonWaitsForFreshSampleAndMatchingNetwork() {
        val baseline = ConnectionDoctorEngine.observeConnected(network(), false, now)!!
        assertEquals(ConnectionDoctorEngine.CompareState.MISSING_BASELINE,
            ConnectionDoctorEngine.compare(null, network(), false, now).state)
        assertEquals(ConnectionDoctorEngine.CompareState.DEMO_BLOCKED,
            ConnectionDoctorEngine.compare(baseline, network(timestamp = now + 1_000L), true, now + 2_000L).state)
        assertEquals(ConnectionDoctorEngine.CompareState.NEED_FRESH_SCAN,
            ConnectionDoctorEngine.compare(baseline, network(timestamp = now - 1_000L), false, now + 2_000L).state)
        assertEquals(ConnectionDoctorEngine.CompareState.DIFFERENT_NETWORK,
            ConnectionDoctorEngine.compare(baseline, network(ssid = "Neighbor", timestamp = now + 1_000L),
                false, now + 2_000L).state)
    }

    @Test fun sameNetworkReportsOnlyObservedDeltas() {
        val before = ConnectionDoctorEngine.observeConnected(network(), false, now)!!
        val after = network(frequency = 2437, rssi = -59, timestamp = now + 2_000L,
            bssid = "11:22:33:44:55:66", linkMbps = 180)
        val compared = ConnectionDoctorEngine.compare(before, after, false, now + 3_000L)
        assertEquals(ConnectionDoctorEngine.CompareState.COMPARABLE, compared.state)
        assertEquals(11, compared.rssiDeltaDb)
        assertEquals(30, compared.linkRateDeltaMbps)
        assertTrue(compared.channelChanged)
        assertTrue(compared.accessPointChanged)
    }

    @Test fun privateBaselineSurvivesReinitializationAndClears() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ConnectionBaselineStore(context)
        val observation = ConnectionDoctorEngine.observeConnected(network(), false, now)!!
        try {
            store.save(observation)
            assertEquals(observation, ConnectionBaselineStore(context).load())
        } finally {
            store.clear()
        }
        assertNull(ConnectionBaselineStore(context).load())
    }
}

