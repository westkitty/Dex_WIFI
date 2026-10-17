package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.WifiBand
import com.example.service.RadioAuditEngine
import com.example.service.RouterAdminPolicy
import com.example.ui.WifiViewModel

/**
 * The Audit tab exposes measured/observed data separately from demo observations.
 * Router modifications are performed only by the user in the external admin UI.
 */
@Composable
fun RadioOperationsScreen(viewModel: WifiViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val networks by viewModel.networks.collectAsState()
    val connected by viewModel.connectedNetwork.collectAsState()
    val demo by viewModel.isSimulatedMode.collectAsState()
    val points by viewModel.surveyPoints.collectAsState()
    val layout by viewModel.currentLayout.collectAsState()
    val history by viewModel.signalHistory.collectAsState()
    var search by remember { mutableStateOf("") }
    var band by remember { mutableStateOf<WifiBand?>(null) }
    var sort by remember { mutableStateOf(RadioAuditEngine.Sort.STRONGEST) }
    var minimum by remember { mutableStateOf(-100f) }
    var uniqueSsid by remember { mutableStateOf(false) }
    var hideHidden by remember { mutableStateOf(false) }
    var insecureOnly by remember { mutableStateOf(false) }
    var weakThreshold by remember { mutableStateOf(-75f) }
    var gatewayText by remember { mutableStateOf("") }
    var targetChannelText by remember { mutableStateOf("") }
    var baseline by remember { mutableStateOf<RouterAdminPolicy.Baseline?>(null) }
    var adminOpened by remember { mutableStateOf(false) }
    var verifiedState by remember { mutableStateOf<String?>(null) }
    val filtered = RadioAuditEngine.filter(networks, RadioAuditEngine.Filter(
        text = search, band = band, minimumRssi = minimum.toInt(), sort = sort,
        onePerSsid = uniqueSsid, includeHidden = !hideHidden, insecureOnly = insecureOnly
    ))
    val stats = RadioAuditEngine.statistics(networks)
    val observedAt = System.currentTimeMillis()
    val detectedGateway = remember { RouterAdminPolicy.discoveredGateway(context) }
    fun share(text: String, mime: String, subject: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, subject))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .testTag("radio_audit_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RADIO AUDIT LAB", style = MaterialTheme.typography.titleLarge)
                Text(if (demo) "DEMO DATA - no apartment measurements"
                    else "ANDROID WI-FI OBSERVATIONS - not router configuration proof")
                Text("Visible access points: ${stats.networkCount} / SSIDs: ${stats.ssidCount} / BSSIDs: ${stats.bssidCount}")
                Text("2.4 / 5 / 6 GHz: ${stats.bands[WifiBand.BAND_2_4GHZ]} / ${stats.bands[WifiBand.BAND_5GHZ]} / ${stats.bands[WifiBand.BAND_6GHZ]}")
                Text("Multi-AP SSIDs: ${stats.meshSsidCount} | Hidden: ${stats.hiddenCount}")
                Text("Security notices - Open: ${stats.openCount}, WEP: ${stats.wepCount}, WPS: ${stats.wpsCount}")
                Text("Weak observed signals: ${stats.weakCount} | Nonstandard 2.4 channels: ${stats.overlapping24Count}")
                Text("Latest scan sample: ${stats.newestScanMs?.let { RadioAuditEngine.ageLabel(it, observedAt) } ?: "No scan"}")
                Text("Signal median (last 30 display samples): ${RadioAuditEngine.rssiMedian(history)?.toString() ?: "N/A"} dBm")
                Text("Displayed signal range: ${RadioAuditEngine.rssiSpread(history)?.toString() ?: "N/A"} dB")
                Button(onClick = { viewModel.triggerScan() }) { Text("Refresh Android scan") }
                Button(onClick = { share(RadioAuditEngine.shareableSummary(networks, points, demo), "text/plain",
                    "Redacted Dex_WIFI audit") }) { Text("Share privacy-safe audit") }
                Text("Network inventory", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(search, { search = it.take(80) }, label = { Text("Search SSID, BSSID or security") },
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Pair<String,WifiBand?>>("All" to null, "2.4" to WifiBand.BAND_2_4GHZ,
                        "5" to WifiBand.BAND_5GHZ, "6" to WifiBand.BAND_6GHZ).forEach { (label, value) ->
                        FilterChip(selected = band == value, onClick = { band = value }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RadioAuditEngine.Sort.entries.forEach { option ->
                        FilterChip(sort == option, onClick = { sort = option },
                            label = { Text(option.name.lowercase()) })
                    }
                }
                Text("Minimum signal filter: ${minimum.toInt()} dBm")
                Slider(value = minimum, onValueChange = { minimum = it }, valueRange = -100f..-30f)
                Row { Checkbox(checked = uniqueSsid, onCheckedChange = { uniqueSsid = it }); Text("One strongest AP per SSID") }
                Row { Checkbox(checked = hideHidden, onCheckedChange = { hideHidden = it }); Text("Hide unnamed networks") }
                Row { Checkbox(checked = insecureOnly, onCheckedChange = { insecureOnly = it }); Text("Show insecure networks only") }
                Text("Matched networks: ${filtered.size}")
            }
        }
        items(filtered, key = { it.bssid + it.frequency }) { n ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                Text("${n.ssid.ifBlank { "(hidden)" }} ${n.rssi} dBm",
                    style = MaterialTheme.typography.titleSmall)
                Text("${n.band.displayName} • Ch ${n.channel} • ${n.channelWidthMhz} MHz • ${n.securityType}")
                Text("${n.bssid} • ${RadioAuditEngine.signalQualityTag(n.rssi)} • ${RadioAuditEngine.ageLabel(n.timestamp, observedAt)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("CHANNEL EVIDENCE", style = MaterialTheme.typography.titleMedium)
                listOf(WifiBand.BAND_2_4GHZ, WifiBand.BAND_5GHZ, WifiBand.BAND_6GHZ).forEach { b ->
                    Text(b.displayName + " observed: " +
                        RadioAuditEngine.observedChannels(networks, b).joinToString { "Ch ${it.first} (${it.second})" }.ifEmpty { "None" })
                    val choices = RadioAuditEngine.channelChoices(networks, b).take(3)
                    if (choices.isNotEmpty()) Text("Heuristic candidates: " +
                        choices.joinToString { "Ch ${it.channel} (relative score ${it.score})" })
                }
                Text("These are observations and heuristic rankings. Regulatory domain, DFS, client support, interference, airtime and router policy are not confirmed.")
                Text("SURVEY COVERAGE", style = MaterialTheme.typography.titleMedium)
                Text("Saved points on current map: ${points.size}")
                Text("Weak threshold: ${weakThreshold.toInt()} dBm")
                Slider(value = weakThreshold, onValueChange = { weakThreshold = it }, valueRange = -90f..-55f)
                Text("Points below threshold: ${RadioAuditEngine.weakSurveyPoints(points, weakThreshold.toInt()).size}")
                Text("Readings older than 14 days: ${RadioAuditEngine.staleSurveyPoints(points, observedAt).size}")
                RadioAuditEngine.sampleCountByBand(points).forEach { (b,c) ->
                    Text("Survey readings ${b.displayName}: $c")
                }
                RadioAuditEngine.rooms(layout, points).forEach { room ->
                    Text("${room.name}: ${room.count} recorded; weakest ${room.worstRssi?.toString() ?: "unmeasured"} dBm")
                }
                Button(enabled = !demo && points.isNotEmpty(), onClick = {
                    share(RadioAuditEngine.exportSurveyCsv(points, "Android hardware"), "text/csv", "Dex_WIFI survey CSV")
                }) { Text("Export measured survey CSV") }
                Text(if (demo) "Export disabled: simulator readings are not physical measurements."
                    else "CSV export includes network names and room notes. Select a trusted recipient.")
                Text("ROUTER ADMIN & CHANGE VERIFICATION", style = MaterialTheme.typography.titleMedium)
                Text("Step 1: identify your authorized router. Admin credentials stay in its own browser; Dex_WIFI never stores them.")
                Text("Wi-Fi gateway detected: ${detectedGateway ?: "Unavailable - manual LAN IP needed"}")
                OutlinedTextField(value = gatewayText, onValueChange = { gatewayText = it.take(100) },
                    label = { Text("Local router address (private IPv4 only)") }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(detectedGateway ?: "10.0.0.1") })
                val gateway = RouterAdminPolicy.safeGatewayUrl(gatewayText.ifBlank { detectedGateway.orEmpty() })
                Text(gateway ?: "Enter a valid private router IPv4 address (10.x, 172.16-31.x, 192.168.x)")
                OutlinedTextField(value = targetChannelText, onValueChange = { targetChannelText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Target channel (after checking router's allowed channels)") })
                val target = targetChannelText.toIntOrNull()?.takeIf { it in 1..233 }
                Button(enabled = !demo && connected != null && target != null, onClick = {
                    val n = connected!!
                    baseline = RouterAdminPolicy.Baseline(n.ssid, n.band.displayName, n.channel,
                        System.currentTimeMillis())
                    verifiedState = null
                    adminOpened = false
                }) { Text("Capture real before-change baseline") }
                baseline?.let { b ->
                    Text("Before: ${b.ssid}, ${b.band} channel ${b.channel}; requested channel ${target ?: "not entered"}")
                    Text("Back up the router's existing settings. Changing Wi-Fi can disconnect the tablet and other devices.")
                }
                Button(enabled = gateway != null && baseline != null && target != null, onClick = {
                    val uri = Uri.parse(gateway!!)
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
                    adminOpened = true
                }) { Text("Log in and apply in router admin") }
                if (adminOpened) {
                    Text("Step 2: authenticate in the router's own interface; inspect supported band/channel settings, save manually, then reconnect. Some ISP gateways lock channels to Auto.")
                }
                Button(enabled = baseline != null && !demo && connected != null && target != null, onClick = {
                    viewModel.triggerScan()
                    val n = connected
                    verifiedState = RouterAdminPolicy.verify(baseline, target, n?.ssid,
                        n?.band?.displayName, n?.channel, n?.timestamp ?: 0L, demo).name
                }) { Text("Check observed channel after reconnect") }
                verifiedState?.let { Text("Observation verdict: $it. This does not prove administrative change without router readback.") }
                baseline?.let { b ->
                    Text("Rollback: return to the router admin page, restore channel ${b.channel} on ${b.band}, save and verify reconnection. Do not reset the router.")
                }
            }
        }
    }
}
