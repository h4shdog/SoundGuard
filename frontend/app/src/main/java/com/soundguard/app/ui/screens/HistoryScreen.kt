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

    var filterType          by remember { mutableStateOf("All") }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var selectMode          by remember { mutableStateOf(false) }
    val selectedIds         = remember { mutableStateListOf<Int>() }

    val types = listOf("All", "Fire Alarm", "Siren")

    val filtered = AlertRepository.history.filter { r ->
        filterType == "All" || r.soundClass.label == filterType
    }

    // ── Confirm delete-all dialog ──────────────────────────────
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = {
                Icon(Icons.Default.DeleteForever, null, tint = FireRed, modifier = Modifier.size(28.dp))
            },
            title = { Text("Delete All History", fontWeight = FontWeight.Bold) },
            text  = {
                Text("This will permanently remove all ${AlertRepository.history.size} alert records. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        AlertRepository.deleteAllHistory()
                        showDeleteAllDialog = false
                        selectMode = false
                        selectedIds.clear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FireRed)
                ) { Text("Delete All") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Title row ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Alert History",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "${filtered.size} record${if (filtered.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectMode) {
                    // Cancel
                    OutlinedButton(
                        onClick = { selectMode = false; selectedIds.clear() },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Cancel") }
                    // Delete selected
                    Button(
                        onClick = {
                            selectedIds.forEach { AlertRepository.deleteRecord(it) }
                            selectedIds.clear()
                            selectMode = false
                        },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete (${selectedIds.size})")
                    }
                } else if (AlertRepository.history.isNotEmpty()) {
                    // Select manually
                    OutlinedButton(
                        onClick = { selectMode = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CheckBox, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Select")
                    }
                    // Delete all
                    Button(
                        onClick = { showDeleteAllDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = FireRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("All")
                    }
                }
            }
        }

        // ── Filters ────────────────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Filter",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
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
                            selectedContainerColor = Color.Black,
                            selectedLabelColor     = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // ── List ───────────────────────────────────────────
        if (filtered.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = TextTertiary)
                    Text(
                        "No records match your filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        } else {
            val player = rememberSoundSamplePlayer()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { record ->
                    val isSelected = record.id in selectedIds
                    HistoryCard(
                        record         = record,
                        player         = player,
                        selectMode     = selectMode,
                        isSelected     = isSelected,
                        onToggleSelect = {
                            if (isSelected) selectedIds.remove(record.id)
                            else selectedIds.add(record.id)
                        },
                        onDelete = { AlertRepository.deleteRecord(record.id) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ── History card ───────────────────────────────────────────────
@Composable
fun HistoryCard(
    record: AlertRecord,
    player: SoundSamplePlayer,
    selectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val isDark = isSystemDark()
    val (accentColor, chipBg) = when (record.soundClass) {
        SoundClass.FIRE_ALARM -> Pair(FireRed,    FireRedLight)
        SoundClass.SIREN      -> Pair(SirenAmber, SirenAmberLight)
        SoundClass.BACKGROUND -> Pair(TextTertiary, if (isDark) DarkSurfaceVar else SurfaceVar)
    }

    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, Color.Black, RoundedCornerShape(16.dp))
                else Modifier
            )
    ) {
        SgCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (selectMode) onToggleSelect()
                    else expanded = !expanded
                },
            topAccentColor = accentColor
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Checkbox in select mode, icon circle otherwise
                if (selectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor   = Color.Black,
                            checkmarkColor = Color.White
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(chipBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            record.soundClass.icon,
                            contentDescription = record.soundClass.label,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (record.soundClass != SoundClass.BACKGROUND) "Possible ${record.soundClass.label}"
                            else record.soundClass.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
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

                // Right side
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                        Text(
                            sText,
                            style = MaterialTheme.typography.labelSmall,
                            color = sFg,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!selectMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Per-card delete icon
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete record",
                                    tint = FireRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ── Expanded detail ────────────────────────────
            if (expanded && !selectMode) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = if (isDark) DarkBorder else Border, thickness = 0.5.dp)
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailStat("Confidence", "${(record.confidence * 100).toInt()}%", accentColor)
                    DetailStat("Inference",  "${record.inferenceMs}ms",               TextTertiary)
                    DetailStat("Model",      record.model.displayName,                Primary)
                    DetailStat("Record #",   "#${record.id}",                         TextTertiary)
                }

                if (record.soundClass != SoundClass.BACKGROUND) {
                    Spacer(Modifier.height(12.dp))

                    val path       = record.audioPath
                    val hasAudio   = path != null
                    val isThisPlaying = player.isPlaying && player.currentPath == path

                    OutlinedButton(
                        onClick  = { if (hasAudio) player.togglePath(path!!) },
                        enabled  = hasAudio,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor        = if (isThisPlaying) accentColor else TextSecondary,
                            disabledContentColor = TextTertiary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (!hasAudio)       TextTertiary.copy(alpha = 0.3f)
                            else if (isThisPlaying) accentColor
                            else                 TextSecondary.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            if (isThisPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isThisPlaying) "Stop" else "Play",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                !hasAudio      -> "No Recording Saved"
                                isThisPlaying  -> "Stop"
                                else           -> "Play Detected Audio"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ── Detail stat cell ──────────────────────────────────────────
@Composable
private fun DetailStat(label: String, value: String, valueColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}
