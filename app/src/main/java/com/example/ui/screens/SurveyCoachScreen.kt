package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.model.ApartmentLayout
import com.example.model.FloorPlanDocument
import com.example.model.SurveyPoint
import com.example.model.WifiBand
import com.example.service.SurveyCoachEngine
import com.example.ui.WifiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SurveyCoachScreen(
    viewModel: WifiViewModel,
    onOpenHeatmap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layout by viewModel.currentLayout.collectAsState()
    val readings by viewModel.surveyPoints.collectAsState()
    val simulated by viewModel.isSimulatedMode.collectAsState()
    val cohorts = remember(readings) { SurveyCoachEngine.availableCohorts(readings) }
    var cohortChoice by remember { mutableStateOf<SurveyCoachEngine.Cohort?>(null) }
    var radius by remember { mutableFloatStateOf(3.5f) }
    var ageDays by remember { mutableFloatStateOf(14f) }
    var analysisRevision by remember { mutableIntStateOf(0) }
    val cohort = cohortChoice?.takeIf { it in cohorts } ?: cohorts.firstOrNull()
    val report by produceState<SurveyCoachEngine.Report?>(
        initialValue = null, layout, readings, cohort, radius, ageDays, analysisRevision
    ) {
        value = null
        value = withContext(Dispatchers.Default) {
            SurveyCoachEngine.analyze(layout, readings, cohort,
                nowMillis = System.currentTimeMillis(),
                radiusMeters = radius, maxAgeDays = ageDays.toInt())
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .testTag("survey_coach_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SURVEY COACH", style = MaterialTheme.typography.headlineSmall)
                Text("Walk your apartment and take independent measurements. Suggested locations are based on spacing, not fabricated signal values.")
                Text(if (simulated) "SIMULATED MODE — no real readings can be recorded"
                    else "Hardware mode — samples remain subject to device limitations",
                    color = if (simulated) Color(0xFFFFC266) else Color(0xFF6CC5A8))
                if (layout.id != FloorPlanDocument.LAYOUT_ID) {
                    Text("Select and calibrate your own apartment in Heatmap before surveying.")
                } else if (!layout.referenceCalibrated) {
                    Text("Calibrate the scale in Floor Plan Studio first.")
                }
                Button(onClick = onOpenHeatmap, modifier = Modifier.testTag("survey_record_map")) {
                    Text("Open heatmap to record a real reading")
                }
                Text("Saved network cohorts", style = MaterialTheme.typography.titleMedium)
                if (cohorts.isEmpty()) Text("No verified hardware observations saved for this layout.")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cohorts, key = { it.label }) { c ->
                        FilterChip(selected = c == cohort, onClick = { cohortChoice = c },
                            label = { Text(c.label) })
                    }
                }
                Text("Exclude readings older than " + ageDays.toInt() + " days")
                Slider(value = ageDays, onValueChange = { ageDays = it },
                    valueRange = 1f..90f, steps = 88)
                Text("Distance considered spatially sampled: " + "%.1f".format(radius) + " m")
                Slider(value = radius, onValueChange = { radius = it },
                    valueRange = .5f..7f)
                Button(onClick = { analysisRevision++ }) { Text("Recheck age and survey quality") }
                if (report == null) {
                    CircularProgressIndicator(modifier = Modifier.testTag("survey_coach_calculating"))
                } else {
                    val summary = report!!
                    Text("MEASUREMENT READINESS", style = MaterialTheme.typography.titleMedium)
                    Text(summary.reason)
                    Text("Spatial proximity sampled: " +
                        (summary.proximityPercent?.toString()?.plus("%") ?: "Not available"))
                    Text("Unsampled proximity: " +
                        (summary.uncoveredPercent?.toString()?.plus("%") ?: "Not available"))
                    Text("These percentages measure proximity to saved points, NOT Wi-Fi coverage or speed.")
                    Text("Valid recent points: " + summary.freshReadings +
                        " of " + summary.selectedReadings + " in selected network/band")
                    Text("Invalid / stale / future-dated: " + summary.invalidReadings +
                        " / " + summary.staleReadings + " / " + summary.futureReadings)
                    Text("Other SSIDs or bands excluded: " + summary.mismatchedReadings)
                    Text("Outside traced apartment: " + summary.outOfBounds +
                        " • Repeated positions: " + summary.duplicateLocations)
                    Text("Channel changes within cohort: " + summary.changedChannelReadings +
                        " • Weak readings: " + summary.weakReadings)
                    Text("Near perimeter: " + summary.edgeSampleCount +
                        " • Nearest-neighbor median spacing: " +
                        (summary.medianSpacingMeters?.let { "%.1f m".format(it) } ?: "N/A"))
                    SurveyCoachMap(layout, readings, cohort, summary)
                    Text("ROOM CHECKLIST", style = MaterialTheme.typography.titleMedium)
                    if (summary.roomProgress.isEmpty()) Text("Add room outlines and names in Floor Plan Studio.")
                    summary.roomProgress.forEach { room ->
                        Text((if (room.requiresAttention) "Needs attention — " else "Sampled — ") +
                            room.room + ": " + room.samples + " readings")
                    }
                    Text("WHERE TO MEASURE NEXT", style = MaterialTheme.typography.titleMedium)
                    summary.suggestedNext.forEachIndexed { index, point ->
                        Text((index + 1).toString() + ". " + point.room +
                            " (map " + "%.2f".format(point.point.x) + ", " +
                            "%.2f".format(point.point.y) + "), nearest saved point " +
                            (if (point.nearestSampleMeters.isFinite())
                                "%.1f m".format(point.nearestSampleMeters) else "none"))
                    }
                    Text("UNCERTAINTIES", style = MaterialTheme.typography.titleMedium)
                    summary.warnings.forEach { Text("• " + it) }
                }
            }
        }
    }
}

@Composable
private fun SurveyCoachMap(
    layout: ApartmentLayout,
    readings: List<SurveyPoint>,
    cohort: SurveyCoachEngine.Cohort?,
    report: SurveyCoachEngine.Report
) {
    val matching = remember(readings, cohort) {
        readings.filter { cohort != null && it.primarySsid == cohort.ssid &&
            it.frequency > 0 && WifiBand.fromFrequency(it.frequency) == cohort.band }
    }
    val roomStroke = Color(0xFF40B6CF)
    val selectedColor = Color(0xFF5FDBC0)
    val nextColor = Color(0xFFFFD26C)
    Canvas(
        modifier = Modifier.fillMaxWidth().height(270.dp)
            .border(1.dp, Color.Gray)
            .background(Color(0xFF0C1822))
            .semantics { contentDescription =
                "Apartment plan. Green circles are stored reading locations; yellow circles are suggested next positions." }
            .testTag("survey_coach_suggested_map")
    ) {
        val width = size.width
        val height = size.height
        if (layout.outline.size >= 3) {
            val path = Path().apply {
                moveTo(layout.outline[0].x * width, layout.outline[0].y * height)
                layout.outline.drop(1).forEach { lineTo(it.x * width, it.y * height) }
                close()
            }
            drawPath(path, roomStroke, style = Stroke(width = 3f))
        } else {
            drawRect(roomStroke, style = Stroke(width = 3f))
        }
        layout.rooms.forEach { room ->
            drawRect(roomStroke.copy(alpha = .4f),
                topLeft = Offset(room.leftNorm * width, room.topNorm * height),
                size = androidx.compose.ui.geometry.Size(room.widthNorm * width,
                    room.heightNorm * height),
                style = Stroke(width = 2f))
        }
        matching.forEach { sample ->
            if (sample.position.x in 0f..1f && sample.position.y in 0f..1f) {
                drawCircle(selectedColor, radius = 7f, center =
                    Offset(sample.position.x * width, sample.position.y * height))
            }
        }
        report.suggestedNext.forEachIndexed { index, suggestion ->
            val pos = Offset(suggestion.point.x * width, suggestion.point.y * height)
            drawCircle(nextColor, radius = 12f, center = pos, style = Stroke(width = 3f))
            drawCircle(nextColor, radius = 4f, center = pos)
        }
    }
    Text("Green = recorded locations. Yellow = suggested locations. Map coordinates are approximate.",
        style = MaterialTheme.typography.bodySmall)
}

