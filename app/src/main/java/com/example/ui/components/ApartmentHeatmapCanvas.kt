package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApartmentLayout
import com.example.model.HeatmapCell
import com.example.model.NormalizedPoint
import com.example.model.SurveyPoint
import com.example.service.HeatmapInterpolator
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberOutline
import com.example.ui.theme.CyberSurfaceHigh
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SignalCritical
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalFair
import com.example.ui.theme.SignalGood
import com.example.ui.theme.SignalWeak

@Composable
fun ApartmentHeatmapCanvas(
    layout: ApartmentLayout,
    surveyPoints: List<SurveyPoint>,
    routerPosition: NormalizedPoint,
    heatmapGrid: List<HeatmapCell>,
    showHeatmap: Boolean = true,
    isRelocateRouterMode: Boolean = false,
    onCanvasTap: (NormalizedPoint, String) -> Unit,
    onDeleteSurveyPoint: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember { mutableStateOf<SurveyPoint?>(null) }
    val pulseAnim = remember { Animatable(0.2f) }

    LaunchedEffect(Unit) {
        pulseAnim.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("apartment_heatmap_canvas_container")
    ) {
        Surface(
            color = CyberBackground,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(layout, isRelocateRouterMode) {
                            detectTapGestures { tapOffset ->
                                val normX = (tapOffset.x / size.width).coerceIn(0f, 1f)
                                val normY = (tapOffset.y / size.height).coerceIn(0f, 1f)
                                val tappedPoint = NormalizedPoint(normX, normY)

                                // Check if user tapped an existing survey point pin (within 24dp hit radius)
                                val hitRadiusPx = 28.dp.toPx()
                                val clickedSurvey = surveyPoints.find { pt ->
                                    val ptX = pt.position.x * size.width
                                    val ptY = pt.position.y * size.height
                                    kotlin.math.hypot(tapOffset.x - ptX, tapOffset.y - ptY) < hitRadiusPx
                                }

                                if (clickedSurvey != null) {
                                    selectedPoint = clickedSurvey
                                    return@detectTapGestures
                                }

                                // Identify which room this point is inside
                                val room = layout.rooms.find { r ->
                                    normX in r.leftNorm..(r.leftNorm + r.widthNorm) &&
                                            normY in r.topNorm..(r.topNorm + r.heightNorm)
                                }
                                val roomName = room?.name ?: "Apartment Floor"

                                selectedPoint = null
                                onCanvasTap(tappedPoint, roomName)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. Draw Heatmap Interpolation Layer
                    if (showHeatmap && heatmapGrid.isNotEmpty()) {
                        val cellW = w / 36f
                        val cellH = h / 28f

                        heatmapGrid.forEach { cell ->
                            val color = HeatmapInterpolator.getColorForRssi(cell.estimatedRssi)
                            // Draw soft translucent rectangle for heatmap matrix
                            drawRect(
                                color = color.copy(alpha = 0.36f),
                                topLeft = Offset((cell.xNorm * w) - (cellW / 2f), (cell.yNorm * h) - (cellH / 2f)),
                                size = Size(cellW + 1f, cellH + 1f)
                            )
                        }
                    }

                    // 2. Draw Floor Plan Room Zones & Walls
                    val wallStroke = Stroke(width = 2.5.dp.toPx())
                    val roomNamePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                        isFakeBoldText = true
                        isAntiAlias = true
                        alpha = 180
                    }

                    layout.rooms.forEach { room ->
                        val rx = room.leftNorm * w
                        val ry = room.topNorm * h
                        val rw = room.widthNorm * w
                        val rh = room.heightNorm * h

                        // Room background tint
                        drawRoundRect(
                            color = CyberSurfaceHigh.copy(alpha = if (showHeatmap) 0.15f else 0.45f),
                            topLeft = Offset(rx, ry),
                            size = Size(rw, rh),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )

                        // Room walls
                        drawRoundRect(
                            color = CyberOutline,
                            topLeft = Offset(rx, ry),
                            size = Size(rw, rh),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = wallStroke
                        )

                        // Room Name Label
                        drawContext.canvas.nativeCanvas.drawText(
                            room.name,
                            rx + 12.dp.toPx(),
                            ry + 22.dp.toPx(),
                            roomNamePaint
                        )
                    }

                    // 3. Draw Outer Apartment Perimeter
                    drawRoundRect(
                        color = NeonCyan.copy(alpha = 0.7f),
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(w - 8.dp.toPx(), h - 8.dp.toPx()),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // 4. Draw Router Position (Pulsing waves)
                    val rX = routerPosition.x * w
                    val rY = routerPosition.y * h

                    // Pulse rings
                    drawCircle(
                        color = NeonCyan.copy(alpha = (1f - pulseAnim.value) * 0.45f),
                        radius = 32.dp.toPx() * pulseAnim.value,
                        center = Offset(rX, rY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = NeonCyan,
                        radius = 9.dp.toPx(),
                        center = Offset(rX, rY)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 4.dp.toPx(),
                        center = Offset(rX, rY)
                    )

                    val routerLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.CYAN
                        textSize = 24f
                        isFakeBoldText = true
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "ROUTER",
                        rX,
                        rY + 20.dp.toPx(),
                        routerLabelPaint
                    )

                    // 5. Draw Survey Measurement Pins
                    surveyPoints.forEach { pt ->
                        val px = pt.position.x * w
                        val py = pt.position.y * h
                        val pinColor = HeatmapInterpolator.getColorForRssi(pt.rssi.toFloat())

                        // Pin shadow
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.6f),
                            radius = 12.dp.toPx(),
                            center = Offset(px, py + 2.dp.toPx())
                        )

                        // Outer ring
                        drawCircle(
                            color = pinColor,
                            radius = 11.dp.toPx(),
                            center = Offset(px, py)
                        )
                        // Inner core
                        drawCircle(
                            color = CyberBackground,
                            radius = 6.dp.toPx(),
                            center = Offset(px, py)
                        )

                        // RSSI value bubble badge
                        val badgePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 20f
                            isFakeBoldText = true
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            "${pt.rssi}",
                            px,
                            py - 14.dp.toPx(),
                            badgePaint
                        )
                    }
                }

                // Helper overlay hint
                if (isRelocateRouterMode) {
                    Surface(
                        color = NeonCyan.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = "Tap anywhere on floor plan to place router",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Selected Pin Inspector Card
        if (selectedPoint != null) {
            val pt = selectedPoint!!
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = CyberSurfaceHigh,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(HeatmapInterpolator.getColorForRssi(pt.rssi.toFloat()))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${pt.roomName} • ${pt.rssi} dBm",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "SSID: ${pt.primarySsid} (Ch ${pt.channel})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pt.notes.isNotEmpty()) {
                            Text(
                                text = "Note: ${pt.notes}",
                                fontSize = 11.sp,
                                color = NeonEmerald
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onDeleteSurveyPoint(pt.id)
                            selectedPoint = null
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete measurement",
                            tint = SignalCritical,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Color Legend Bar
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-90 dBm (Dead Zone)", color = SignalCritical, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                SignalCritical,
                                SignalWeak,
                                SignalFair,
                                SignalGood,
                                SignalExcellent
                            )
                        )
                    )
            )

            Text("-40 dBm (Peak)", color = SignalExcellent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
