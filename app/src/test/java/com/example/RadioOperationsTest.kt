package com.example

import com.example.model.NormalizedPoint
import com.example.model.SurveyPoint
import com.example.model.WifiBand
import com.example.model.WifiNetwork
import com.example.service.RadioAuditEngine
import com.example.service.RouterAdminPolicy
import org.junit.Assert.*
import org.junit.Test

class RadioOperationsTest {
    private val aps = listOf(
        WifiNetwork(ssid="Flat",bssid="00:01",frequency=2437,rssi=-42,capabilities="[WPA2]",timestamp=12000),
        WifiNetwork(ssid="Flat",bssid="00:02",frequency=5240,rssi=-59,capabilities="[WPA3]",timestamp=14000),
        WifiNetwork(ssid="=EXCEL()",bssid="00:03",frequency=2462,rssi=-87,capabilities="[OPEN][WPS]",timestamp=15000),
        WifiNetwork(ssid="",bssid="00:04",frequency=5180,rssi=-67,capabilities="[WEP]",timestamp=11000)
    )
    @Test fun filterInventoryAndChannelChoices() {
        assertEquals(2,RadioAuditEngine.filter(aps,RadioAuditEngine.Filter(text="flat")).size)
        assertEquals(3,RadioAuditEngine.filter(aps,RadioAuditEngine.Filter(onePerSsid=true)).size)
        assertEquals(2,RadioAuditEngine.filter(aps,RadioAuditEngine.Filter(insecureOnly=true)).size)
        assertEquals(-87,RadioAuditEngine.filter(aps,RadioAuditEngine.Filter(sort=RadioAuditEngine.Sort.WEAKEST)).first().rssi)
        val s=RadioAuditEngine.statistics(aps)
        assertEquals(4,s.networkCount)
        assertEquals(3,s.ssidCount)
        assertEquals(1,s.meshSsidCount)
        assertEquals(1,s.hiddenCount)
        assertEquals(1,s.openCount)
        assertEquals(1,s.wepCount)
        assertEquals(1,s.wpsCount)
        assertEquals(11000L,s.oldestScanMs)
        assertEquals(2,s.bands[WifiBand.BAND_5GHZ])
        assertEquals(3,RadioAuditEngine.channelChoices(aps,WifiBand.BAND_2_4GHZ).size)
        assertTrue(RadioAuditEngine.channelChoices(aps,WifiBand.BAND_6GHZ).isEmpty())
    }
    @Test fun surveyExportEscapesUntrustedStringsAndSummaryRedactsNames() {
        val p=SurveyPoint(5,"home",NormalizedPoint(.1f,.2f),"=HYPERLINK","@SSID",-55,2437,6,12L,"+CMD")
        val csv=RadioAuditEngine.exportSurveyCsv(listOf(p),"HARDWARE")
        assertTrue(csv.contains("'=HYPERLINK"))
        assertTrue(csv.contains("'@SSID"))
        assertTrue(csv.contains("'+CMD"))
        assertFalse(RadioAuditEngine.shareableSummary(aps,listOf(p),false).contains("EXCEL()"))
        assertEquals(-60,RadioAuditEngine.rssiMedian(listOf(-75,-50,-60)))
        assertEquals(25,RadioAuditEngine.rssiSpread(listOf(-75,-50,-60)))
        assertNull(RadioAuditEngine.rssiMedian(emptyList()))
    }
    @Test fun localRouterAddressAndVerificationAreFailClosed() {
        assertEquals("http://10.0.0.1/",RouterAdminPolicy.safeGatewayUrl("10.0.0.1"))
        assertEquals("https://192.168.1.1:8443/",RouterAdminPolicy.safeGatewayUrl("https://192.168.1.1:8443"))
        listOf("https://google.com","https://user:pw@10.0.0.1","http://10.0.0.1/?password=1",
            "http://127.0.0.1","http://169.254.1.1","10.0.0.999","10.0.0.1@evil.com").forEach {
            assertNull(it,RouterAdminPolicy.safeGatewayUrl(it))
        }
        val baseline=RouterAdminPolicy.Baseline("Flat","2.4 GHz",6,900)
        assertEquals(RouterAdminPolicy.Verification.NO_REAL_DATA,
            RouterAdminPolicy.verify(baseline,11,"Flat","2.4 GHz",11,1100,true))
        assertEquals(RouterAdminPolicy.Verification.NOT_YET_OBSERVED,
            RouterAdminPolicy.verify(baseline,11,"Flat","2.4 GHz",11,800,false))
        assertEquals(RouterAdminPolicy.Verification.STILL_OLD_CHANNEL,
            RouterAdminPolicy.verify(baseline,11,"Flat","2.4 GHz",6,1100,false))
        assertEquals(RouterAdminPolicy.Verification.TARGET_CHANNEL_OBSERVED,
            RouterAdminPolicy.verify(baseline,11,"Flat","2.4 GHz",11,1100,false))
    }
}
