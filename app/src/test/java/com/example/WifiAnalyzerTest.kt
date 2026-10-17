package com.example

import com.example.model.ApartmentLayout
import com.example.model.NormalizedPoint
import com.example.model.RoomType
import com.example.model.RoomZone
import com.example.model.SurveyPoint
import com.example.model.WifiBand
import com.example.model.WifiNetwork
import com.example.service.HeatmapInterpolator
import com.example.service.WifiAnalyzerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAnalyzerTest {

    @Test
    fun testFrequencyToChannelConversion() {
        assertEquals(1, WifiBand.frequencyToChannel(2412))
        assertEquals(6, WifiBand.frequencyToChannel(2437))
        assertEquals(11, WifiBand.frequencyToChannel(2462))
        assertEquals(14, WifiBand.frequencyToChannel(2484))
        assertEquals(36, WifiBand.frequencyToChannel(5180))
        assertEquals(48, WifiBand.frequencyToChannel(5240))
        assertEquals(149, WifiBand.frequencyToChannel(5745))
    }

    @Test
    fun testWifiBandClassification() {
        assertEquals(WifiBand.BAND_2_4GHZ, WifiBand.fromFrequency(2437))
        assertEquals(WifiBand.BAND_5GHZ, WifiBand.fromFrequency(5240))
        assertEquals(WifiBand.BAND_6GHZ, WifiBand.fromFrequency(6100))
    }

    @Test
    fun testChannelInterferenceScoring() {
        // Create 3 networks on Channel 6 (heavy congestion) and 0 on Channel 1
        val networks = listOf(
            WifiNetwork(ssid = "Net1", bssid = "00:11", rssi = -50, frequency = 2437),
            WifiNetwork(ssid = "Net2", bssid = "00:22", rssi = -65, frequency = 2437),
            WifiNetwork(ssid = "Net3", bssid = "00:33", rssi = -70, frequency = 2437)
        )

        val analyses = WifiAnalyzerEngine.analyzeChannels(networks, WifiBand.BAND_2_4GHZ)
        val ch6 = analyses.find { it.channel == 6 }
        val ch1 = analyses.find { it.channel == 1 }

        assertNotNull(ch6)
        assertNotNull(ch1)
        assertEquals(3, ch6!!.coChannelCount)
        assertEquals(0, ch1!!.coChannelCount)
        assertTrue("Channel 1 should score higher than congested Channel 6", ch1.bestScore > ch6.bestScore)
    }

    @Test
    fun testHeatmapGridCalculation() {
        val layout = ApartmentLayout(
            id = "test_apt",
            name = "Test Apt",
            description = "Test",
            widthMeters = 10f,
            heightMeters = 10f,
            rooms = listOf(RoomZone("room1", "Room 1", 0f, 0f, 1f, 1f, RoomType.LIVING_ROOM)),
            defaultRouterPosition = NormalizedPoint(0.5f, 0.5f)
        )

        val surveyPoints = listOf(
            SurveyPoint(1, "test_apt", NormalizedPoint(0.5f, 0.5f), "Center", "Net", -40, 5240, 48),
            SurveyPoint(2, "test_apt", NormalizedPoint(0.9f, 0.9f), "Far Corner", "Net", -82, 5240, 48)
        )

        val grid = HeatmapInterpolator.computeHeatmapGrid(layout, surveyPoints, layout.defaultRouterPosition, 10, 10)
        assertEquals(100, grid.size)

        // Point near center should have strong signal
        val centerCell = grid.find { it.xNorm in 0.45f..0.55f && it.yNorm in 0.45f..0.55f }
        assertNotNull(centerCell)
        assertTrue(centerCell!!.estimatedRssi > -60f)
    }
}
