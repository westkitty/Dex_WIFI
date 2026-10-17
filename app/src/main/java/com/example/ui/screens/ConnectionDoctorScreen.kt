package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.service.ConnectionBaselineStore
import com.example.service.ConnectionDoctorEngine
import com.example.ui.WifiViewModel
import kotlinx.coroutines.launch

@Composable
fun ConnectionDoctorScreen(viewModel: WifiViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val connected by viewModel.connectedNetwork.collectAsState()
    val simulated by viewModel.isSimulatedMode.collectAsState()
    val scope = rememberCoroutineScope()
    val store = remember(context) { ConnectionBaselineStore(context) }
    var baseline by remember { mutableStateOf(store.load()) }
    var snapshotRevision by remember { mutableIntStateOf(0) }
    val link = remember(context, snapshotRevision) {
        ConnectionDoctorEngine.connectionSnapshot(context)
    }
    var adminProbe by remember { mutableStateOf<ConnectionDoctorEngine.Probe?>(null) }
    var dnsProbe by remember { mutableStateOf<ConnectionDoctorEngine.Probe?>(null) }
    var runningRouterProbe by remember { mutableStateOf(false) }
    var runningDnsProbe by remember { mutableStateOf(false) }
    var comparison by remember { mutableStateOf<ConnectionDoctorEngine.Comparison?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("connection_doctor_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("CONNECTION DOCTOR", style = MaterialTheme.typography.headlineSmall)
                Text("Diagnose your tablet's current connection without changing router settings.")
                Text(if (simulated) "SIMULATION — connection tests cannot validate a demo radio"
                    else "Android live connectivity. Router and DNS probes run only on explicit request.")
                Text("ANDROID NETWORK STATE", style = MaterialTheme.typography.titleMedium)
                Text("Default network: " + if (link.activeNetwork) "present" else "absent")
                Text("Default transport: " + if (link.wifiDefault) "Wi-Fi" else "not Wi-Fi (or hidden by VPN)")
                Text("Android Internet validation: " + if (link.osValidated) "validated" else "not validated / unknown")
                Text("Captive portal indicated: " + if (link.captivePortal) "yes" else "no / unknown")
                Text("Metered classification: " + if (link.metered) "metered or unknown" else "not metered")
                Text("IP interface: " + (link.interfaceName ?: "unavailable"))
                Text("Gateway admin address: " + (link.gatewayUrl ?: "unavailable"))
                Text("Configured DNS resolvers: " +
                    link.dnsServers.joinToString().ifBlank { "unavailable" })
                Text("Interface MTU: " + (link.mtu?.toString() ?: "unavailable"))
                Text("System downstream estimate: " +
                    (link.downstreamKbps?.toString()?.plus(" Kbit/s") ?: "unavailable") +
                    " (not a speed test)")
                Text("Connected Wi-Fi: " + (if (simulated) "demo source blocked"
                    else connected?.ssid ?: "unknown"))
                Text("Channel: " + (if (simulated) "N/A" else connected?.channel?.toString() ?: "N/A"))
                Text("Radio strength: " + (if (simulated) "N/A" else
                    connected?.rssi?.toString()?.plus(" dBm") ?: "N/A"))
                Text("PHY link rate: " + (if (simulated) "N/A" else
                    connected?.linkSpeedMbps?.toString()?.plus(" Mbps") ?: "unavailable") +
                    " (not actual throughput)")
                Text("Network observation age: " +
                    (connected?.timestamp?.takeIf { it > 0L }?.let { ts ->
                        val age = System.currentTimeMillis() - ts
                        if (age >= 0) (age / 1000L).toString() + " seconds"
                        else "clock mismatch"
                    } ?: "unknown / cached"))
                OutlinedButton(onClick = {
                    snapshotRevision++
                    viewModel.triggerScan()
                    comparison = null
                }, modifier = Modifier.testTag("connection_refresh")) {
                    Text("Refresh Android status and request Wi-Fi scan")
                }
                Text("SAFE, ON-DEMAND CHECKS", style = MaterialTheme.typography.titleMedium)
                Text("The local check attempts only the gateway's web port. A closed port is NOT proof of Wi-Fi failure.")
                Button(enabled = !runningRouterProbe && link.wifiDefault &&
                    link.gatewayUrl != null && !simulated, onClick = {
                    runningRouterProbe = true
                    scope.launch {
                        try { adminProbe = ConnectionDoctorEngine.testRouterWebPort(link.gatewayUrl) }
                        finally { runningRouterProbe = false }
                    }
                }, modifier = Modifier.testTag("doctor_router_port_test")) {
                    Text(if (runningRouterProbe) "Testing gateway web port..." else "Test local gateway web port")
                }
                adminProbe?.let {
                    Text("Router web port: " + (if (it.success) "answered" else "not answered") +
                        " | " + it.description +
                        (it.durationMs?.let { n -> " (" + n + " ms TCP handshake)" } ?: ""))
                }
                Text("DNS lookup contacts the configured resolver for example.com. It is optional, not a background test.")
                Button(enabled = !runningDnsProbe && link.activeNetwork && !simulated,
                    onClick = {
                        runningDnsProbe = true
                        scope.launch {
                            try { dnsProbe = ConnectionDoctorEngine.testDnsExplicitly() }
                            finally { runningDnsProbe = false }
                        }
                    }, modifier = Modifier.testTag("doctor_dns_test")) {
                    Text(if (runningDnsProbe) "Resolving example.com..." else "Run optional DNS lookup")
                }
                dnsProbe?.let {
                    Text("DNS: " + (if (it.success) "resolved" else "not resolved") +
                        " | " + it.description +
                        (it.durationMs?.let { n -> " (" + n + " ms)" } ?: ""))
                }

                Text("BEFORE / AFTER RECORD", style = MaterialTheme.typography.titleMedium)
                Text("Capture a fresh real-radio baseline before changing router settings. This stays in the application's private storage.")
                Button(enabled = !simulated && connected != null, onClick = {
                    val measured = ConnectionDoctorEngine.observeConnected(
                        connected, simulated, System.currentTimeMillis())
                    if (measured == null) {
                        captureError = "The radio reading is old, missing, or simulated. Refresh and obtain a new hardware scan."
                    } else {
                        store.save(measured)
                        baseline = measured
                        captureError = null
                        comparison = null
                    }
                }, modifier = Modifier.testTag("doctor_capture_baseline")) { Text("Capture fresh before-change baseline") }
                captureError?.let { Text(it) }
                baseline?.let { old ->
                    Text("Saved before: " + old.ssid + " / " + old.band + " / channel " + old.channel +
                        ", " + old.rssi + " dBm")
                    Text("Baseline time (UTC milliseconds): " + old.capturedAt)
                    Text("After reconnecting, refresh the Wi-Fi scan, wait for a newer observation, then compare.")
                    Button(enabled = !simulated, onClick = {
                        comparison = ConnectionDoctorEngine.compare(
                            old, connected, simulated, System.currentTimeMillis())
                    }, modifier = Modifier.testTag("doctor_compare")) {
                        Text("Compare latest real observation")
                    }
                    comparison?.let { result ->
                        Text("Comparison verdict: " + result.state.name)
                        if (result.state == ConnectionDoctorEngine.CompareState.COMPARABLE) {
                            Text("RSSI change: " + result.rssiDeltaDb + " dB (positive is stronger)")
                            Text("Reported link-rate change: " +
                                (result.linkRateDeltaMbps?.toString()?.plus(" Mbps") ?: "unknown") +
                                " — not measured throughput")
                            Text("Channel changed: " + result.channelChanged +
                                " | Access point changed: " + result.accessPointChanged)
                            Text("Only immediate observations are compared. No latency, throughput or device-wide improvement is implied.")
                        } else {
                            Text("No valid before/after improvement is claimed. Check the network and obtain a fresh scan.")
                        }
                    }
                    OutlinedButton(onClick = {
                        store.clear()
                        baseline = null
                        comparison = null
                    }) { Text("Clear saved baseline") }
                }
                Text("Your router-admin login and any configuration changes happen in the existing Audit Lab handoff, never inside Connection Doctor.")
            }
        }
    }
}

