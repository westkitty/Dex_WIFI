package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChannelSpectrumCurve
import com.example.model.WifiBand
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberOutline
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import kotlin.math.cos

val SpectrumColors = listOf(
    NeonCyan,
    NeonEmerald,
    NeonAmber,
    NeonPurple,
    NeonBlue,
    Color(0xFFFF4081),
    Color(0xFF00E5FF),
    Color(0xFFFFAB00)
)

@Composable
fun ChannelSpectrumCanvas(
    band: WifiBand,
    curves: List<ChannelSpectrumCurve>,
    modifier: Modifier = Modifier
) {
    var selectedCurve by remember { mutableStateOf<ChannelSpectrumCurve?>(null) }

    Surface(
        color = CyberBackground,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("channel_spectrum_canvas")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${band.displayName} SPECTRUM DISTRIBUTION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${curves.size} Networks",
                    fontSize = 11.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Spectrum Graph Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(curves) {
                            detectTapGestures { tapOffset ->
                                // Optional tap hit test could select a curve
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    val leftMargin = 32.dp.toPx()
                    val bottomMargin = 26.dp.toPx()
                    val chartW = w - leftMargin
                    val chartH = h - bottomMargin

                    val minRssi = -100f
                    val maxRssi = -35f
                    val rssiRange = maxRssi - minRssi

                    // Draw Horizontal dBm Grid Lines (-40, -60, -80, -100)
                    val dbmValues = listOf(-40, -60, -80, -100)
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 24f
                        isAntiAlias = true
                    }

                    dbmValues.forEach { dbm ->
                        val normY = 1f - ((dbm - minRssi) / rssiRange)
                        val y = normY * chartH
                        drawLine(
                            color = CyberOutline.copy(alpha = 0.5f),
                            start = Offset(leftMargin, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "$dbm",
                            2.dp.toPx(),
                            y + 8f,
                            textPaint
                        )
                    }

                    // Define Channel Axis parameters
                    val (channels, startCh, endCh) = if (band == WifiBand.BAND_2_4GHZ) {
                        Triple(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14), 0.5f, 14.5f)
                    } else {
                        Triple(listOf(36, 40, 44, 48, 52, 56, 60, 64, 149, 153, 157, 161, 165), 32f, 168f)
                    }

                    // Draw Channel markers & standard non-overlapping channel highlights
                    channels.forEach { ch ->
                        val normX = (ch - startCh) / (endCh - startCh)
                        val x = leftMargin + (normX * chartW)

                        // Vertical guide line
                        drawLine(
                            color = CyberOutline.copy(alpha = 0.25f),
                            start = Offset(x, 0f),
                            end = Offset(x, chartH),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Label channel
                        val isAnchor = (band == WifiBand.BAND_2_4GHZ && (ch == 1 || ch == 6 || ch == 11))
                        val chPaint = android.graphics.Paint().apply {
                            color = if (isAnchor) android.graphics.Color.CYAN else android.graphics.Color.LTGRAY
                            textSize = if (isAnchor) 28f else 22f
                            isFakeBoldText = isAnchor
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            "$ch",
                            x,
                            h - 4.dp.toPx(),
                            chPaint
                        )
                    }

                    // Draw Network Spectrum Curves (Parabolic bell curves)
                    curves.forEach { curve ->
                        val color = if (curve.isConnected) NeonCyan else SpectrumColors[curve.colorIndex % SpectrumColors.size]

                        val normCenterX = (curve.channel - startCh) / (endCh - startCh)
                        val centerX = leftMargin + (normCenterX * chartW)

                        // Bell width in channel coordinates (20 MHz is approx 4 channel steps in 2.4G)
                        val chSpan = if (band == WifiBand.BAND_2_4GHZ) {
                            if (curve.bandwidthMhz >= 40) 8.0f else 4.2f
                        } else {
                            if (curve.bandwidthMhz >= 80) 16f else if (curve.bandwidthMhz >= 40) 8f else 4f
                        }
                        val widthPx = (chSpan / (endCh - startCh)) * chartW

                        // Peak Y
                        val peakNormY = 1f - ((curve.peakRssi.coerceIn(-100, -35) - minRssi) / rssiRange)
                        val peakY = peakNormY * chartH

                        val bellPath = Path()
                        val fillPath = Path()

                        val steps = 30
                        val halfW = widthPx / 2f
                        val leftX = centerX - halfW
                        val rightX = centerX + halfW

                        fillPath.moveTo(leftX.coerceAtLeast(leftMargin), chartH)
                        bellPath.moveTo(leftX.coerceAtLeast(leftMargin), chartH)

                        for (i in 0..steps) {
                            val t = i / steps.toFloat() // 0 to 1
                            val curX = leftX + (t * widthPx)
                            if (curX < leftMargin || curX > w) continue

                            // Raised cosine bell shape: 0 at ends, 1 at center
                            val factor = 0.5f * (1f + cos((t - 0.5f) * 2f * Math.PI.toFloat()))
                            val curY = chartH - (factor * (chartH - peakY))

                            if (i == 0) {
                                bellPath.moveTo(curX, curY)
                                fillPath.lineTo(curX, curY)
                            } else {
                                bellPath.lineTo(curX, curY)
                                fillPath.lineTo(curX, curY)
                            }
                        }

                        fillPath.lineTo(rightX.coerceAtMost(w), chartH)
                        fillPath.close()

                        // Draw filled gradient under curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = if (curve.isConnected) 0.38f else 0.18f), Color.Transparent),
                                startY = peakY,
                                endY = chartH
                            )
                        )

                        // Draw outer stroke
                        drawPath(
                            path = bellPath,
                            color = color,
                            style = Stroke(
                                width = if (curve.isConnected) 3.dp.toPx() else 1.8.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )

                        // Draw SSID tag near peak
                        val tagPaint = android.graphics.Paint().apply {
                            this.color = if (curve.isConnected) android.graphics.Color.WHITE else android.graphics.Color.LTGRAY
                            textSize = 20f
                            isFakeBoldText = curve.isConnected
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val cleanSsid = if (curve.ssid.length > 12) curve.ssid.take(10) + ".." else curve.ssid
                        drawContext.canvas.nativeCanvas.drawText(
                            cleanSsid,
                            centerX.coerceIn(leftMargin + 30f, w - 30f),
                            peakY - 8f,
                            tagPaint
                        )
                    }
                }
            }

            // Legend / Highlights
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(NeonCyan)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Your Network", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(NeonAmber)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Neighbors / Interference", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
    }
}
