package com.soundguard.app.ui.screens

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.soundguard.app.data.*
import com.soundguard.app.service.SoundMonitorService
import com.soundguard.app.ui.components.*
import com.soundguard.app.ui.theme.*

@Composable
fun MonitorScreen() {
    val isDark       = isSystemDark()
    val bg           = if (isDark) DarkBackground else Background
    val isRunning    = AlertRepository.isRunning
    val detection    = AlertRepository.current
    val errorMessage = AlertRepository.errorMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Error Banner ───────────────────────────────────────
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FireRedBg)
                    .border(1.dp, FireRed, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = FireRed, modifier = Modifier.size(20.dp))
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = FireRed)
            }
        }

        // ── Live Monitoring Card ───────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {

            // Card header row: title + badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "Live Monitoring",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Real-time AI classification probabilities",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                // Status badge
                StatusBadge(isRunning)
            }

            Spacer(Modifier.height(12.dp))

            if (!isRunning) {
                // ── Paused / idle state ────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, if (isDark) DarkBorder else Border, RoundedCornerShape(12.dp))
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Tap the microphone to start monitoring.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = if (isDark) TextSecondary else TextPrimary
                    )
                }
            } else {
                // ── Active / running state ─────────────────────
                val det = detection ?: DetectionResult(
                    soundClass  = SoundClass.BACKGROUND,
                    confidence  = 0f,
                    inferenceMs = 0,
                    model       = ModelType.BEST_MODEL,
                    isEmergency = false,
                    allScores   = floatArrayOf(0f, 0f, 0f)
                )

                // Detection display box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, if (isDark) DarkBorder else Border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DetectionRing(det)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Status label row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val statusColor = when {
                                    det.isEmergency && det.soundClass == SoundClass.FIRE_ALARM -> FireRed
                                    det.isEmergency && det.soundClass == SoundClass.SIREN      -> SirenAmber
                                    else -> SafeGreen
                                }
                                PulsingDot(statusColor)
                                Text(
                                    if (det.isEmergency) "ALERT" else "MONITORING",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            // Sound class name
                            Text(
                                if (det.isEmergency) "Possible ${det.soundClass.label}"
                                else det.soundClass.label,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            // Confidence + latency
                            Text(
                                "${(det.confidence * 100).toInt()}% - ${det.inferenceMs}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Confidence bars ────────────────────────────────
            // Show 0% when not running, live values when running
            val det = if (isRunning) (detection ?: DetectionResult(allScores = floatArrayOf(0f, 0f, 0f)))
                      else DetectionResult(allScores = floatArrayOf(0f, 0f, 0f))

            val fireScore  = det.allScores.getOrElse(0) { 0f }
            val bgScore    = det.allScores.getOrElse(1) { 0f }
            val sirenScore = det.allScores.getOrElse(2) { 0f }

            // Priming message (4-second buffer fill when running but no data yet)
            if (isRunning && fireScore == 0f && bgScore == 0f && sirenScore == 0f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulsingDot(Primary, size = 10.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Listening… (collecting first 4 s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }

            HearConfidenceBar(
                label = "Fire Alarm",
                value = fireScore,
                color = FireRed,
                labelColor = FireRed,
                isDark = isDark
            )
            Spacer(Modifier.height(10.dp))
            HearConfidenceBar(
                label = "Siren",
                value = sirenScore,
                color = SirenAmber,
                labelColor = SirenAmber,
                isDark = isDark
            )
            Spacer(Modifier.height(10.dp))
            HearConfidenceBar(
                label = "Background Noise",
                value = bgScore,
                color = if (isDark) Color(0xFF64748B) else Color(0xFF334155),
                labelColor = if (isDark) TextSecondary else TextPrimary,
                isDark = isDark
            )
        }

        // ── Alert Modes Card ───────────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {

            // Header row: title + CUSTOMIZABLE badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Alert Modes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Configure notification responses",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isDark) DarkSurfaceVar else Color(0xFFE8E8E8))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "CUSTOMIZABLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextSecondary else TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val modes = remember {
                mutableStateListOf(
                    Triple(Icons.Default.Vibration,     "Vibration",       true),
                    Triple(Icons.Default.FlashOn,       "Visual Flash",    true),
                    Triple(Icons.Default.Watch,         "Watch Companion", true),
                    Triple(Icons.Default.Notifications, "Screen Alert",    true)
                )
            }

            modes.forEachIndexed { i, (icon, label, checked) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon in a rounded square bg
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) DarkSurfaceVar else Color(0xFFF0F0F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon, null,
                            tint = if (isDark) TextSecondary else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = checked,
                        onCheckedChange = { modes[i] = Triple(icon, label, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = Color.White,
                            checkedTrackColor  = Color.Black,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = if (isDark) DarkBorder else Border
                        )
                    )
                }
                if (i < modes.size - 1) {
                    HorizontalDivider(
                        color = if (isDark) DarkBorder else Border,
                        thickness = 0.5.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Status badge (ACTIVE / PAUSED) ────────────────────────────
@Composable
private fun StatusBadge(isRunning: Boolean) {
    val bgColor   = if (isRunning) SafeGreen.copy(alpha = 0.15f) else SirenAmber.copy(alpha = 0.15f)
    val dotColor  = if (isRunning) SafeGreen else SirenAmber
    val textColor = if (isRunning) SafeGreen else SirenAmber
    val label     = if (isRunning) "ACTIVE" else "PAUSED"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isRunning) {
            PulsingDot(dotColor, size = 8.dp)
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ── Detection ring ─────────────────────────────────────────────
@Composable
fun DetectionRing(detection: DetectionResult) {
    val color = when (detection.soundClass) {
        SoundClass.FIRE_ALARM -> FireRed
        SoundClass.SIREN      -> SirenAmber
        SoundClass.BACKGROUND -> Color(0xFF334155)
    }
    val bg = when (detection.soundClass) {
        SoundClass.FIRE_ALARM -> FireRedBg
        SoundClass.SIREN      -> SirenAmberBg
        SoundClass.BACKGROUND -> SafeGreenBg
    }
    val inf = rememberInfiniteTransition(label = "ring")
    val scale by inf.animateFloat(
        1f, if (detection.isEmergency) 1.12f else 1.04f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ringScale"
    )
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .border(3.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            detection.soundClass.icon,
            contentDescription = detection.soundClass.label,
            tint = color,
            modifier = Modifier.size(36.dp)
        )
    }
}

// ── Confidence bar styled to match the Hearmergency design ─────
@Composable
private fun HearConfidenceBar(
    label: String,
    value: Float,
    color: Color,
    labelColor: Color,
    isDark: Boolean
) {
    val anim by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(600),
        label = "confAnim"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = labelColor
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { anim },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = if (isDark) DarkBorder else Border
        )
    }
}
