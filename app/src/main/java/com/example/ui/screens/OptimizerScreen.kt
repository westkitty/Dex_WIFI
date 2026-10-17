package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.model.SuggestionCategory
import com.example.model.SuggestionSeverity
import com.example.model.WifiOptimizationSuggestion
import com.example.ui.WifiViewModel
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberOutline
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceHigh
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SignalCritical
import com.example.ui.theme.SignalExcellent
import com.example.ui.theme.SignalFair
import com.example.ui.theme.SignalGood
import com.example.ui.theme.SignalWeak

@Composable
fun OptimizerScreen(
    viewModel: WifiViewModel,
    modifier: Modifier = Modifier
) {
    val suggestions by viewModel.optimizationSuggestions.collectAsState()
    val connected by viewModel.connectedNetwork.collectAsState()
    val surveyPoints by viewModel.surveyPoints.collectAsState()

    var showRouterHelpDialog by remember { mutableStateOf(false) }

    // Overall Apartment Health Score Calculation
    val criticalCount = suggestions.count { it.severity == SuggestionSeverity.CRITICAL }
    val warningCount = suggestions.count { it.severity == SuggestionSeverity.WARNING }
    val healthScore = (100 - (criticalCount * 25) - (warningCount * 12)).coerceIn(35, 100)

    val healthColor = when {
        healthScore >= 80 -> SignalExcellent
        healthScore >= 60 -> SignalFair
        else -> SignalCritical
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .testTag("optimizer_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Overall Health Score Card
        item {
            Surface(
                color = CyberSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("health_score_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "APARTMENT WI-FI HEALTH",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (healthScore >= 80) "Optimal Configuration" else if (healthScore >= 60) "Moderate Interference" else "Severe Congestion & Dead Zones",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Big Score Ring
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(healthColor.copy(alpha = 0.15f))
                                .border(2.dp, healthColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$healthScore",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = healthColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metrics Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetricPill(
                            label = "Current Band",
                            value = connected?.band?.displayName ?: "N/A",
                            color = NeonCyan
                        )
                        MetricPill(
                            label = "Channel",
                            value = connected?.channel?.toString() ?: "N/A",
                            color = NeonCyan
                        )
                        MetricPill(
                            label = "Dead Zones",
                            value = "${surveyPoints.count { it.rssi < -78 }}",
                            color = if (surveyPoints.any { it.rssi < -78 }) SignalCritical else NeonEmerald
                        )
                    }
                }
            }
        }

        // Router Admin Guide Button
        item {
            Surface(
                color = CyberSurfaceHigh,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRouterHelpDialog = true }
                    .testTag("router_admin_guide_btn")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "How to Apply Router Channel Changes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Step-by-step guide to logging into your apartment router",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Suggestions List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OPTIMIZATION RECOMMENDATIONS (${suggestions.size})",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        if (suggestions.isEmpty()) {
            item {
                Surface(
                    color = CyberSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No optimization issues detected!",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Your apartment Wi-Fi settings are currently optimal.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(suggestions, key = { it.id }) { sugg ->
                SuggestionCard(suggestion = sugg)
            }
        }
    }

    // Router Admin Access Dialog
    if (showRouterHelpDialog) {
        AlertDialog(
            onDismissRequest = { showRouterHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("How to Change Wi-Fi Channel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Open your web browser on any phone or laptop connected to your Wi-Fi.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "2. Navigate to your router IP (commonly http://192.168.1.1, 192.168.0.1, or 10.0.0.1).",
                        fontSize = 12.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "3. Log in using the admin credentials printed on the physical sticker on your router bottom.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "4. Go to Wireless Settings > Channel and change from 'Auto' to the recommended channel number.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "5. Click 'Apply / Save'. Devices will reconnect in ~15 seconds with reduced interference!",
                        fontSize = 12.sp,
                        color = NeonEmerald
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRouterHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                ) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SuggestionCard(suggestion: WifiOptimizationSuggestion) {
    var isExpanded by remember { mutableStateOf(false) }

    val (badgeColor, badgeText) = when (suggestion.severity) {
        SuggestionSeverity.CRITICAL -> SignalCritical to "High Priority"
        SuggestionSeverity.WARNING -> NeonAmber to "Warning"
        SuggestionSeverity.OPTIMIZATION -> NeonCyan to "Speed Boost"
        SuggestionSeverity.GOOD -> NeonEmerald to "Optimal"
    }

    Surface(
        color = CyberSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("suggestion_card_${suggestion.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Priority Pill
                Surface(
                    color = badgeColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = suggestion.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = suggestion.summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Impact badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = NeonEmerald,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = suggestion.impact,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonEmerald
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .background(CyberSurfaceVariant, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    if (suggestion.currentSetting != null && suggestion.suggestedSetting != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Current Setting", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(suggestion.currentSetting, fontSize = 11.sp, color = SignalWeak, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.CenterVertically)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Recommended", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(suggestion.suggestedSetting, fontSize = 11.sp, color = NeonEmerald, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = "Action:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = suggestion.recommendedAction,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
