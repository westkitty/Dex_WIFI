package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SignalQuality
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberOutline
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SignalCritical
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalFair
import com.example.ui.theme.SignalGood
import com.example.ui.theme.SignalWeak

@Composable
fun SignalQualityBadge(
    quality: SignalQuality,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (quality) {
        SignalQuality.EXCELLENT -> SignalExcellent.copy(alpha = 0.2f) to SignalExcellent
        SignalQuality.GOOD -> SignalGood.copy(alpha = 0.2f) to SignalGood
        SignalQuality.FAIR -> SignalFair.copy(alpha = 0.2f) to SignalFair
        SignalQuality.WEAK -> SignalWeak.copy(alpha = 0.2f) to SignalWeak
        SignalQuality.CRITICAL -> SignalCritical.copy(alpha = 0.2f) to SignalCritical
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = quality.label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SignalRadarGauge(
    rssi: Int,
    percent: Int,
    modifier: Modifier = Modifier
) {
    val pulseAnim = remember { Animatable(0.4f) }

    LaunchedEffect(Unit) {
        pulseAnim.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    val primaryColor = when {
        rssi >= -55 -> SignalExcellent
        rssi >= -67 -> SignalGood
        rssi >= -75 -> SignalFair
        rssi >= -85 -> SignalWeak
        else -> SignalCritical
    }

    Box(
        modifier = modifier
            .size(110.dp)
            .testTag("signal_radar_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 8.dp.toPx()

            // Concentric target rings
            for (i in 1..3) {
                drawCircle(
                    color = CyberOutline.copy(alpha = 0.4f),
                    radius = radius * (i / 3f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Radar pulse ring
            drawCircle(
                color = primaryColor.copy(alpha = (1f - pulseAnim.value) * 0.5f),
                radius = radius * pulseAnim.value,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Outer gauge arc representing percentage
            val sweep = (percent / 100f) * 270f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(primaryColor.copy(alpha = 0.3f), primaryColor)
                ),
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$rssi",
                color = primaryColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "dBm",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun LiveSignalWaveform(
    history: List<Int>,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CyberBackground,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
        modifier = modifier.testTag("live_signal_waveform")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonCyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE RF WAVEFORM",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                val currentRssi = history.lastOrNull() ?: -70
                Text(
                    text = "$currentRssi dBm",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                if (history.size < 2) return@Canvas

                val minVal = -95f
                val maxVal = -35f
                val range = maxVal - minVal

                val w = size.width
                val h = size.height
                val stepX = w / (history.size - 1)

                val points = history.mapIndexed { index, rssi ->
                    val normY = 1f - ((rssi - minVal) / range).coerceIn(0f, 1f)
                    Offset(index * stepX, normY * h)
                }

                val path = Path()
                val fillPath = Path()

                points.forEachIndexed { i, pt ->
                    if (i == 0) {
                        path.moveTo(pt.x, pt.y)
                        fillPath.moveTo(pt.x, h)
                        fillPath.lineTo(pt.x, pt.y)
                    } else {
                        path.lineTo(pt.x, pt.y)
                        fillPath.lineTo(pt.x, pt.y)
                    }
                }
                fillPath.lineTo(w, h)
                fillPath.close()

                // Gradient fill underneath
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )

                // Line stroke
                drawPath(
                    path = path,
                    color = NeonCyan,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Current point glowing dot
                val lastPt = points.last()
                drawCircle(color = NeonCyan, radius = 4.dp.toPx(), center = lastPt)
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = lastPt)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-30s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                Text("-15s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                Text("Now", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}
