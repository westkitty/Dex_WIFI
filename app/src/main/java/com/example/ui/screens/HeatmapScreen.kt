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
import com.example.model.FloorPlanDocument
import com.example.model.NormalizedPoint
import com.example.model.RoomZone
import com.example.service.HeatmapInterpolator
import com.example.ui.WifiViewModel
import com.example.ui.components.ApartmentHeatmapCanvas
import com.example.ui.components.CustomFloorPlanCanvas
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
    val simulated by viewModel.isSimulatedMode.collectAsState()
    val locationGranted by viewModel.hasLocationPermission.collectAsState()
    val customPlan by viewModel.customPlan.collectAsState()
    var editingFloorPlan by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    if (editingFloorPlan) {
        FloorPlanStudio(viewModel, onClose = { editingFloorPlan = false })
        return
    }

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
        // Floor Plan Selector: presets remain available, personal plan is persistent.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APARTMENT BLUEPRINT", color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("%.1f × %.1f m".format(currentLayout.widthMeters,
                        currentLayout.heightMeters), color = NeonCyan, fontSize = 11.sp)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DefaultApartments.allLayouts) { layout ->
                        FilterChip(
                            selected = currentLayout.id == layout.id,
                            onClick = { viewModel.selectLayout(layout) },
                            label = { Text(layout.name, fontSize = 12.sp) },
                            modifier = Modifier.testTag("layout_chip_${layout.id}")
                        )
                    }
                    if (customPlan != null) {
                        item {
                            FilterChip(
                                selected = currentLayout.id == FloorPlanDocument.LAYOUT_ID,
                                onClick = { viewModel.openCustomPlan() },
                                label = { Text(customPlan!!.name, fontSize = 12.sp) },
                                modifier = Modifier.testTag("custom_layout_chip")
                            )
                        }
                    }
                }
                Button(
                    onClick = { editingFloorPlan = true },
                    modifier = Modifier.testTag("open_floor_plan_studio")
                ) {
                    Text(if (customPlan == null) "Draw / photograph my apartment"
                        else "Edit my apartment map", fontSize = 12.sp)
                }
                Text(
                    if (currentLayout.id == FloorPlanDocument.LAYOUT_ID)
                        "My floor plan • private on this device • editable anytime"
                    else "These are example layouts, not verified diagrams of your apartment.",
                    color = if (currentLayout.id == FloorPlanDocument.LAYOUT_ID)
                        NeonEmerald else NeonAmber, fontSize = 11.sp
                )
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

                    // Demonstration points are intentionally unavailable on real custom plans.
                    if (currentLayout.id != FloorPlanDocument.LAYOUT_ID) {
                        TextButton(
                            onClick = { viewModel.loadSampleSurveyPoints() },
                            modifier = Modifier.testTag("sample_survey_btn")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null,
                                tint = NeonAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DEMO", fontSize = 11.sp, color = NeonAmber)
                        }
                    }

                    // Clear Pins
                    IconButton(
                        onClick = { showClearConfirm = true },
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

        // Survey uses precisely the same normalized photo coordinates as the editor.
        item {
            val onTap: (NormalizedPoint, String) -> Unit = { point, roomName ->
                if (isRelocateRouterMode) {
                    viewModel.setRouterPosition(point)
                    isRelocateRouterMode = false
                } else {
                    pendingTapPoint = point to roomName
                    customRoomNotes = ""
                }
            }
            val plan = customPlan
            if (currentLayout.id == FloorPlanDocument.LAYOUT_ID && plan != null) {
                CustomFloorPlanCanvas(
                    document = plan,
                    photoFile = viewModel.floorPlanPhotoFile(plan.photoFile),
                    surveyPoints = surveyPoints,
                    routerPosition = routerPosition,
                    heatmapGrid = heatmapGrid,
                    showHeatmap = showHeatmapOverlay,
                    isRelocateRouterMode = isRelocateRouterMode,
                    onCanvasTap = onTap,
                    onDeleteSurveyPoint = { viewModel.deleteSurveyPoint(it) }
                )
            } else {
                ApartmentHeatmapCanvas(
                    layout = currentLayout,
                    surveyPoints = surveyPoints,
                    routerPosition = routerPosition,
                    heatmapGrid = heatmapGrid,
                    showHeatmap = showHeatmapOverlay,
                    isRelocateRouterMode = isRelocateRouterMode,
                    onCanvasTap = onTap,
                    onDeleteSurveyPoint = { viewModel.deleteSurveyPoint(it) }
                )
            }
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
                            text = if (!viewModel.canRecordRealSurveyPoint()) {
                                 "SIMULATION or unavailable reading. No measurement can be saved until you grant Location, enable Android Location services, and switch to Hardware mode."
                             } else {
                                 "Stand in the room and tap your real location on the plan. Hardware snapshot: ${connected?.rssi} dBm (${connected?.ssid}). Android may throttle scanning."
                             },
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
                surveyPoints = surveyPoints.filter { point ->
                    if (currentLayout.id == FloorPlanDocument.LAYOUT_ID) {
                        point.position.x in room.leftNorm..(room.leftNorm + room.widthNorm) &&
                            point.position.y in room.topNorm..(room.topNorm + room.heightNorm)
                    } else point.roomName == room.name
                }
            )
        }
    }

    // Tap to record survey point dialog
    if (pendingTapPoint != null) {
        val (point, roomName) = pendingTapPoint!!
        val currentRssi = connected?.rssi
        val ssid = connected?.ssid ?: "No active hardware Wi-Fi network"
        val canRecord = !simulated && locationGranted && viewModel.canRecordRealSurveyPoint()

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
                        text = if (canRecord) "Hardware RSSI: $currentRssi dBm ($ssid)"
                             else "No authentic Wi-Fi reading. Fake readings cannot be saved.",
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
                    enabled = canRecord,
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
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Delete these survey readings?") },
            text = { Text("Readings on the selected map will be permanently deleted. The map itself will remain.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearAllSurveyPoints()
                    showClearConfirm = false
                }) { Text("Delete readings") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
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
