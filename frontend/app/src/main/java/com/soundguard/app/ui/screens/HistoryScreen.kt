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
            // One shared player so only one clip plays at a time across all cards
            val player = rememberSoundSamplePlayer()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { record ->
                    HistoryCard(record, player)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }        }
    }
}

@Composable
fun HistoryCard(record: AlertRecord, player: SoundSamplePlayer) {
    val isDark = isSystemDark()
    val (accentColor, chipBg, chipFg) = when (record.soundClass) {
        SoundClass.FIRE_ALARM  -> Triple(FireRed,    FireRedLight,    FireRed)
        SoundClass.SIREN       -> Triple(SirenAmber, SirenAmberLight, SirenAmber)
        SoundClass.BACKGROUND  -> Triple(TextTertiary, if (isDark) DarkSurfaceVar else SurfaceVar, TextSecondary)
    }

    var expanded by remember { mutableStateOf(false) }

    SgCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        topAccentColor = accentColor
    ) {
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

            // Status chip + expand chevron
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ── Expanded detail ────────────────────────────────
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = if (isDark) DarkBorder else Border, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailStat("Confidence", "${(record.confidence * 100).toInt()}%", accentColor)
                DetailStat("Inference", "${record.inferenceMs}ms", TextTertiary)
                DetailStat("Model", record.model.displayName, Primary)
                DetailStat("Record #", "#${record.id}", TextTertiary)
            }

            // ── Play Sample button (only for emergency sound classes) ──
            // To enable: add fire_alarm.mp3 and siren.mp3 to res/raw/
            if (record.soundClass != SoundClass.BACKGROUND) {
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { /* add fire_alarm.mp3 / siren.mp3 to res/raw/ to enable */ },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = TextTertiary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextTertiary.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play Sample", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}
