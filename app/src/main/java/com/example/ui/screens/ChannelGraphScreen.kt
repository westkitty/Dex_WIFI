package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChannelAnalysis
import com.example.model.WifiBand
import com.example.ui.WifiViewModel
import com.example.ui.components.ChannelSpectrumCanvas
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
fun ChannelGraphScreen(
    viewModel: WifiViewModel,
    modifier: Modifier = Modifier
) {
    val selectedBand by viewModel.selectedBand.collectAsState()
    val spectrumCurves by viewModel.spectrumCurves.collectAsState()
    val channelAnalyses by viewModel.channelAnalyses.collectAsState()
    val connected by viewModel.connectedNetwork.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .testTag("channel_graph_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Band Selector Tabs
        item {
            Surface(
                color = CyberSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline)
            ) {
                TabRow(
                    selectedTabIndex = if (selectedBand == WifiBand.BAND_2_4GHZ) 0 else 1,
                    containerColor = Color.Transparent,
                    contentColor = NeonCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(
                                tabPositions[if (selectedBand == WifiBand.BAND_2_4GHZ) 0 else 1]
                            ),
                            color = NeonCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedBand == WifiBand.BAND_2_4GHZ,
                        onClick = { viewModel.selectBand(WifiBand.BAND_2_4GHZ) },
                        text = {
                            Text(
                                "2.4 GHz Band",
                                fontWeight = if (selectedBand == WifiBand.BAND_2_4GHZ) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_24ghz")
                    )
                    Tab(
                        selected = selectedBand == WifiBand.BAND_5GHZ,
                        onClick = { viewModel.selectBand(WifiBand.BAND_5GHZ) },
                        text = {
                            Text(
                                "5 GHz Band",
                                fontWeight = if (selectedBand == WifiBand.BAND_5GHZ) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_5ghz")
                    )
                }
            }
        }

        // Spectrum Distribution Canvas
        item {
            ChannelSpectrumCanvas(
                band = selectedBand,
                curves = spectrumCurves
            )
        }

        // Co-Channel vs Adjacent Interference Educational Note
        item {
            Surface(
                color = CyberSurfaceHigh.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (selectedBand == WifiBand.BAND_2_4GHZ) {
                                "2.4 GHz Apartment Channel Overlap"
                            } else {
                                "5 GHz High Speed Channels"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedBand == WifiBand.BAND_2_4GHZ) {
                                "In 2.4 GHz, only Channels 1, 6, and 11 do not overlap with each other. If a neighbor uses Channel 3 or 4, it radiates interference onto both Channel 1 and 6!"
                            } else {
                                "5 GHz provides much wider 80 MHz channels with far lower wall penetration from neighbors. Ideal for 4K streaming and low-latency gaming."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Channel Ranking Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHANNEL CONGESTION & RATING",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Score: 100 = Cleanest",
                    color = NeonEmerald,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Channel Analysis Cards
        items(channelAnalyses) { analysis ->
            val isCurrentChannel = connected?.band == selectedBand && connected?.channel == analysis.channel

            ChannelRatingCard(
                analysis = analysis,
                isCurrent = isCurrentChannel
            )
        }
    }
}

@Composable
fun ChannelRatingCard(
    analysis: ChannelAnalysis,
    isCurrent: Boolean
) {
    val scoreColor = when {
        analysis.bestScore >= 80 -> SignalExcellent
        analysis.bestScore >= 60 -> SignalGood
        analysis.bestScore >= 45 -> SignalFair
        analysis.bestScore >= 30 -> SignalWeak
        else -> SignalCritical
    }

    Surface(
        color = if (isCurrent) CyberSurfaceHigh else CyberSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) NeonCyan else if (analysis.isRecommended) NeonEmerald.copy(alpha = 0.5f) else CyberOutline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("channel_rating_card_${analysis.channel}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Number Box
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isCurrent) NeonCyan.copy(alpha = 0.2f) else CyberOutline.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CH",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${analysis.channel}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) NeonCyan else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${analysis.centerFreqMhz} MHz",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isCurrent) {
                        Surface(
                            color = NeonCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Your Channel",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (analysis.isRecommended) {
                        Surface(
                            color = NeonEmerald.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Best Pick",
                                    color = NeonEmerald,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${analysis.coChannelCount} co-channel • ${if (analysis.adjacentInterferenceScore > 0) "Overlap detected" else "Clear adjacent"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (analysis.networksOnChannel.isNotEmpty()) {
                    val names = analysis.networksOnChannel.take(2).joinToString { it.ssid }
                    Text(
                        text = "Networks: $names",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Score Pill
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${analysis.bestScore}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                Text(
                    text = if (analysis.bestScore >= 80) "Clean" else if (analysis.bestScore >= 50) "Moderate" else "Congested",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scoreColor
                )
            }
        }
    }
}
