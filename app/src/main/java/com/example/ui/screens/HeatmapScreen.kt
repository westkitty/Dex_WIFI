package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DefaultApartments
import com.example.model.NormalizedPoint
import com.example.model.RoomZone
import com.example.service.HeatmapInterpolator
import com.example.ui.WifiViewModel
import com.example.ui.components.ApartmentHeatmapCanvas
import com.example.ui.components.SignalQualityBadge
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberOutline
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceHigh
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SignalCritical
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalFair
import com.example.ui.theme.SignalGood
import com.example.ui.theme.SignalWeak

@Composable
fun HeatmapScreen(
    viewModel: WifiViewModel,
    modifier: Modifier = Modifier
) {
    val currentLayout by viewModel.currentLayout.collectAsState()
    val surveyPoints by viewModel.surveyPoints.collectAsState()
    val routerPosition by viewModel.routerPosition.collectAsState()
    val heatmapGrid by viewModel.heatmapGrid.collectAsState()
    val connected by viewModel.connectedNetwork.collectAsState()

    var showHeatmapOverlay by remember { mutableStateOf(true) }
    var isRelocateRouterMode by remember { mutableStateOf(false) }

    // Dialog state for adding pin manually
    var pendingTapPoint by remember { mutableStateOf<Pair<NormalizedPoint, String>?>(null) }
    var customRoomNotes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .testTag("heatmap_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Floor Plan Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "APARTMENT BLUEPRINT",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${currentLayout.widthMeters.toInt()}m × ${currentLayout.heightMeters.toInt()}m",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DefaultApartments.allLayouts) { layout ->
                        FilterChip(
                            selected = currentLayout.id == layout.id,
                            onClick = { viewModel.selectLayout(layout) },
                            label = { Text(layout.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan,
                                selectedLabelColor = Color.Black
                            ),
                            modifier = Modifier.testTag("layout_chip_${layout.id}")
                        )
                    }
                }
            }
        }

        // Action Toolbar
        item {
            Surface(
                color = CyberSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Heatmap toggle
                    IconButton(
                        onClick = { showHeatmapOverlay = !showHeatmapOverlay },
                        modifier = Modifier.testTag("toggle_heatmap_btn")
                    ) {
                        Icon(
                            imageVector = if (showHeatmapOverlay) Icons.Default.Layers else Icons.Default.LayersClear,
                            contentDescription = "Toggle Heatmap",
                            tint = if (showHeatmapOverlay) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Move Router
                    IconButton(
                        onClick = { isRelocateRouterMode = !isRelocateRouterMode },
                        modifier = Modifier.testTag("relocate_router_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = "Place Router",
                            tint = if (isRelocateRouterMode) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Load Sample Points
                    TextButton(
                        onClick = { viewModel.loadSampleSurveyPoints() },
                        modifier = Modifier.testTag("sample_survey_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sample Survey", fontSize = 11.sp, color = NeonAmber)
                    }

                    // Clear Pins
                    IconButton(
                        onClick = { viewModel.clearAllSurveyPoints() },
                        modifier = Modifier.testTag("clear_pins_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Pins",
                            tint = SignalCritical
                        )
                    }
                }
            }
        }

        // Main Heatmap Canvas
        item {
            ApartmentHeatmapCanvas(
                layout = currentLayout,
                surveyPoints = surveyPoints,
                routerPosition = routerPosition,
                heatmapGrid = heatmapGrid,
                showHeatmap = showHeatmapOverlay,
                isRelocateRouterMode = isRelocateRouterMode,
                onCanvasTap = { point, roomName ->
                    if (isRelocateRouterMode) {
                        viewModel.setRouterPosition(point)
                        isRelocateRouterMode = false
                    } else {
                        pendingTapPoint = point to roomName
                        customRoomNotes = ""
                    }
                },
                onDeleteSurveyPoint = { id ->
                    viewModel.deleteSurveyPoint(id)
                }
            )
        }

        // Survey Quick Guide
        item {
            Surface(
                color = CyberSurfaceHigh.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLocationAlt,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Walk & Survey Instructions",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Walk to a room in your apartment, then tap that spot on the floor plan to record your live Wi-Fi signal (${connected?.rssi ?: -65} dBm).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Room-by-Room Coverage Breakdown
        item {
            Text(
                text = "ROOM-BY-ROOM COVERAGE (${currentLayout.rooms.size} ZONES)",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        items(currentLayout.rooms) { room ->
            RoomCoverageCard(
                room = room,
                surveyPoints = surveyPoints.filter { it.roomName == room.name }
            )
        }
    }

    // Tap to record survey point dialog
    if (pendingTapPoint != null) {
        val (point, roomName) = pendingTapPoint!!
        val currentRssi = connected?.rssi ?: -65
        val ssid = connected?.ssid ?: "Current Network"

        AlertDialog(
            onDismissRequest = { pendingTapPoint = null },
            title = {
                Text(
                    text = "Record Survey Measurement",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Location: $roomName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonCyan
                    )
                    Text(
                        text = "Recorded Signal: $currentRssi dBm ($ssid)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = customRoomNotes,
                        onValueChange = { customRoomNotes = it },
                        label = { Text("Observation Note (optional)") },
                        placeholder = { Text("e.g. Near desk, behind drywall") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addSurveyPoint(point, roomName, customRoomNotes)
                        pendingTapPoint = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                    modifier = Modifier.testTag("save_survey_point_btn")
                ) {
                    Text("Save Measurement")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingTapPoint = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RoomCoverageCard(
    room: RoomZone,
    surveyPoints: List<com.example.model.SurveyPoint>
) {
    val bestPoint = surveyPoints.maxByOrNull { it.rssi }
    val avgRssi = if (surveyPoints.isNotEmpty()) {
        surveyPoints.map { it.rssi }.average().toInt()
    } else null

    val statusColor = when {
        avgRssi == null -> MaterialTheme.colorScheme.onSurfaceVariant
        avgRssi >= -55 -> SignalExcellent
        avgRssi >= -67 -> SignalGood
        avgRssi >= -75 -> SignalFair
        avgRssi >= -82 -> SignalWeak
        else -> SignalCritical
    }

    Surface(
        color = CyberSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (surveyPoints.isNotEmpty()) {
                        "${surveyPoints.size} measurements recorded"
                    } else {
                        "Not surveyed yet — tap map to test"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (avgRssi != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$avgRssi dBm",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = if (avgRssi < -78) "Dead Zone!" else if (avgRssi >= -65) "Great Coverage" else "Fair",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            } else {
                Text(
                    text = "Untested",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
