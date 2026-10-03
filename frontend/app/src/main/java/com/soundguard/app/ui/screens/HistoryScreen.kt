package com.soundguard.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.soundguard.app.data.*
import com.soundguard.app.ui.components.*
import com.soundguard.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val isDark = isSystemDark()
    val bg     = if (isDark) DarkBackground else Background

    var filterType  by remember { mutableStateOf("All") }

    val types  = listOf("All", "Fire Alarm", "Siren")

    // Live list from repository — updates automatically when service posts detections
    val filtered = AlertRepository.history.filter { r ->
        filterType == "All" || r.soundClass.label == filterType
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Title ──────────────────────────────────────────
        Text("Alert History", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "${filtered.size} records",
            style = MaterialTheme.typography.bodyMedium, color = TextTertiary
        )

        // ── Filters ────────────────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Text("Filter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(Modifier.height(8.dp))

            // Type filter chips
            Text("Type", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { t ->
                    val selected = t == filterType
                    FilterChip(
                        selected = selected,
                        onClick  = { filterType = t },
                        label    = { Text(t, style = MaterialTheme.typography.labelSmall) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor     = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // ── List ───────────────────────────────────────────
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = TextTertiary)
                    Text("No records match your filter", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { record ->
                    HistoryCard(record)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun HistoryCard(record: AlertRecord) {
    val isDark = isSystemDark()
    val (accentColor, chipBg, chipFg) = when (record.soundClass) {
        SoundClass.FIRE_ALARM  -> Triple(FireRed,    FireRedLight,    FireRed)
        SoundClass.SIREN       -> Triple(SirenAmber, SirenAmberLight, SirenAmber)
        SoundClass.BACKGROUND  -> Triple(TextTertiary, if (isDark) DarkSurfaceVar else SurfaceVar, TextSecondary)
    }

    SgCard(modifier = Modifier.fillMaxWidth(), topAccentColor = accentColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(chipBg),
                contentAlignment = Alignment.Center
            ) {
                Text(record.soundClass.emoji, fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(record.soundClass.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    // Model badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFEBF0FF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            record.model.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(record.timestamp, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Confidence: ${(record.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${record.inferenceMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            // Status chip
            val (sBg, sFg, sText) = if (!record.dismissed)
                Triple(SafeGreenBg, SafeGreen, "Confirmed")
            else
                Triple(if (isDark) DarkSurfaceVar else SurfaceVar, TextTertiary, "Dismissed")

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(sBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(sText, style = MaterialTheme.typography.labelSmall, color = sFg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
