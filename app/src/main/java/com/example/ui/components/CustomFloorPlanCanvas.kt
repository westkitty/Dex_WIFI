package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FloorPlanDocument
import com.example.model.HeatmapCell
import com.example.model.NormalizedPoint
import com.example.model.PlanElementType
import com.example.model.PlanGeometry
import com.example.model.SurveyPoint
import com.example.service.HeatmapInterpolator
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberOutline
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SignalCritical
import java.io.File
import kotlin.math.hypot

/** Exact same fractional coordinate frame as the editor, including photographs. */
@Composable
fun CustomFloorPlanCanvas(
    document: FloorPlanDocument,
    photoFile: File?,
    surveyPoints: List<SurveyPoint>,
    heatmapGrid: List<HeatmapCell>,
    routerPosition: NormalizedPoint,
    showHeatmap: Boolean,
    isRelocateRouterMode: Boolean,
    onCanvasTap: (NormalizedPoint, String) -> Unit,
    onDeleteSurveyPoint: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val photo = remember(photoFile?.absolutePath) {
        runCatching { photoFile?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() } }.getOrNull()
    }
    val aspect = (if (photo != null) document.imageAspect else
        document.widthMeters / document.heightMeters).coerceIn(.35f, 4f)
    var selectedPin by remember(surveyPoints) { mutableStateOf<SurveyPoint?>(null) }
    val measuredSsid = surveyPoints.maxByOrNull { it.timestamp }?.primarySsid
    val visibleSamples = surveyPoints.filter { it.primarySsid == measuredSsid }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val canvasWidth = minOf(maxWidth, aspect * 530.dp)
            val canvasHeight = canvasWidth / aspect
            Surface(
                color = CyberBackground,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
                modifier = Modifier.width(canvasWidth).height(canvasHeight)
                    .testTag("custom_floorplan_survey_map")
            ) {
                Box {
                    if (photo != null) {
                        Image(
                            bitmap = photo, contentDescription = "Your imported apartment floor plan",
                            contentScale = ContentScale.FillBounds,
                            alpha = .73f,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    Canvas(modifier = Modifier.matchParentSize().pointerInput(
                        document, surveyPoints, isRelocateRouterMode
                    ) {
                        detectTapGestures { pos ->
                            val x = (pos.x / size.width).coerceIn(0f, 1f)
                            val y = (pos.y / size.height).coerceIn(0f, 1f)
                            val selected = surveyPoints.lastOrNull { pin ->
                                hypot(pos.x - pin.position.x * size.width,
                                    pos.y - pin.position.y * size.height) < 27.dp.toPx()
                            }
                            if (selected != null && !isRelocateRouterMode) {
                                selectedPin = selected
                            } else {
                                val hit = NormalizedPoint(x, y)
                                if (PlanGeometry.pointInOutline(hit,
                                        if (document.outlineClosed) document.outline else emptyList())) {
                                    selectedPin = null
                                    onCanvasTap(hit, PlanGeometry.roomAt(document, hit))
                                }
                            }
                        }
                    }) {
                        val w = size.width
                        val h = size.height
                        // Interpolation has no values for areas without real nearby samples.
                        if (showHeatmap && document.scaleVerified && heatmapGrid.isNotEmpty()) {
                            val stepX = w / 36f
                            val stepY = h / 28f
                            heatmapGrid.forEach { cell ->
                                drawRect(
                                    HeatmapInterpolator.getColorForRssi(cell.estimatedRssi)
                                        .copy(alpha = .39f),
                                    topLeft = Offset(cell.xNorm * w - stepX / 2f,
                                        cell.yNorm * h - stepY / 2f),
                                    size = Size(stepX + 1f, stepY + 1f)
                                )
                            }
                        }
                        val labelPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 26f
                            isAntiAlias = true
                            isFakeBoldText = true
                            setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
                        }
                        document.rooms.forEach { room ->
                            val x = room.leftNorm * w
                            val y = room.topNorm * h
                            val roomSize = Size(room.widthNorm * w, room.heightNorm * h)
                            drawRect(NeonEmerald.copy(alpha = .10f), Offset(x, y), roomSize)
                            drawRect(NeonEmerald.copy(alpha = .5f), Offset(x, y),
                                roomSize, style = Stroke(width = 2f))
                            drawContext.canvas.nativeCanvas.drawText(
                                room.name, x + 8f, y + 27f, labelPaint
                            )
                        }
                        if (document.outline.size >= 2) {
                            val shape = Path().apply {
                                moveTo(document.outline[0].x * w, document.outline[0].y * h)
                                document.outline.drop(1).forEach { lineTo(it.x * w, it.y * h) }
                                if (document.outlineClosed) close()
                            }
                            drawPath(shape, NeonCyan, style = Stroke(width = 5f))
                        }
                        document.segments.forEach { s ->
                            val c = when (s.kind) {
                                PlanElementType.WALL -> NeonCyan
                                PlanElementType.DOOR -> NeonEmerald
                                PlanElementType.WINDOW -> NeonAmber
                            }
                            drawLine(c, Offset(s.start.x * w, s.start.y * h),
                                Offset(s.end.x * w, s.end.y * h),
                                strokeWidth = if (s.kind == PlanElementType.WALL) 6f else 9f)
                        }
                        val rx = routerPosition.x * w
                        val ry = routerPosition.y * h
                        drawCircle(NeonCyan, 12f, Offset(rx, ry))
                        drawCircle(Color.Black, 5f, Offset(rx, ry))
                        drawContext.canvas.nativeCanvas.drawText("ROUTER", rx + 14f, ry - 8f, labelPaint)
                        surveyPoints.forEach { p ->
                            val px = p.position.x * w
                            val py = p.position.y * h
                            val isSelectedSsid = p.primarySsid == measuredSsid
                            val pin = if (isSelectedSsid)
                                HeatmapInterpolator.getColorForRssi(p.rssi.toFloat()) else Color.Gray
                            drawCircle(Color.Black, 16f, Offset(px, py))
                            drawCircle(pin, 12f, Offset(px, py))
                            drawCircle(Color.Black, 6f, Offset(px, py))
                            drawContext.canvas.nativeCanvas.drawText(
                                "${p.rssi}", px + 12f, py - 15f, labelPaint
                            )
                        }
                    }
                }
            }
        }

        if (isRelocateRouterMode) {
            Text("Router mode: tap its real location on your floor plan.",
                color = NeonCyan, fontSize = 12.sp)
        }
        if (!document.scaleVerified) {
            Text("UNCALIBRATED • Set a measured distance in Edit plan before trusting spatial coverage.",
                color = NeonAmber, fontSize = 12.sp)
        } else if (visibleSamples.size < 3) {
            Text("No heatmap yet. Record 3 or more real measurements, spread across the apartment.",
                color = NeonAmber, fontSize = 12.sp)
        } else {
            Text("INTERPOLATED • ${visibleSamples.size} real samples on ${measuredSsid ?: "network"}; blank areas are unmeasured.",
                color = NeonEmerald, fontSize = 12.sp)
        }
        if (selectedPin != null) {
            val pin = selectedPin!!
            Surface(color = CyberSurface, shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${pin.roomName}: ${pin.rssi} dBm",
                            fontWeight = FontWeight.Bold, color = NeonCyan)
                        Text("${pin.primarySsid} • Channel ${pin.channel} • Measured",
                            color = Color.LightGray, fontSize = 12.sp)
                        if (pin.notes.isNotEmpty()) Text(pin.notes, color = Color.LightGray,
                            fontSize = 11.sp)
                    }
                    IconButton(onClick = {
                        onDeleteSurveyPoint(pin.id)
                        selectedPin = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete this reading",
                            tint = SignalCritical)
                    }
                }
            }
        }
    }
}
