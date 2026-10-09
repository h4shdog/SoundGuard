package com.soundguard.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.soundguard.app.data.*
import com.soundguard.app.ui.components.*
import com.soundguard.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val isDark = isSystemDark()
    val bg     = if (isDark) DarkBackground else Background

    // Live data from repository — Compose snapshot state, recomposes automatically
    val isRunning        = AlertRepository.isRunning
    val currentDetection = AlertRepository.current
        ?: DetectionResult(SoundClass.BACKGROUND, 0f, 0, ModelType.BEST_MODEL, false)

    val recentAlerts = AlertRepository.history.take(4)
    val fireCount    = AlertRepository.history.count { it.soundClass == SoundClass.FIRE_ALARM }
    val sirenCount   = AlertRepository.history.count { it.soundClass == SoundClass.SIREN }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Top Bar ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "SoundGuard",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    PulsingDot(if (isRunning) SafeGreen else TextTertiary)
                    Text(
                        if (isRunning) "Monitoring Active" else "Monitoring Stopped",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRunning) SafeGreen else TextTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            // Watch companion indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) DarkSurfaceVar else SurfaceVar)
                    .border(1.dp, if (isDark) DarkBorder else Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Watch, null, tint = Primary, modifier = Modifier.size(16.dp))
                    Text("Watch", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier.size(6.dp).clip(CircleShape).background(SafeGreen)
                    )
                }
            }
        }

        // ── Emergency Banner ─────────────────────────────────
        EmergencyBanner(currentDetection)

        // ── Stat Grid ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Default.LocalFireDepartment,
                iconTint    = FireRed,
                iconBg      = FireRedLight,
                value       = "$fireCount",
                label       = "Fire Alarms",
                trend       = "This session",
                trendColor  = FireRed,
                accentColor = FireRed
            )
            StatCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Default.Campaign,
                iconTint    = SirenAmber,
                iconBg      = SirenAmberLight,
                value       = "$sirenCount",
                label       = "Sirens",
                trend       = "This session",
                trendColor  = SirenAmber,
                accentColor = SirenAmber
            )
        }
        // ── Live Classification Card ─────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth(), topAccentColor = Primary) {
            SectionHeader("Live Classification")
            Spacer(Modifier.height(12.dp))

            // Big result display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon circle
                val (circleBg, circleIcon) = when (currentDetection.soundClass) {
                    SoundClass.FIRE_ALARM  -> Pair(FireRedBg,    FireRed)
                    SoundClass.SIREN       -> Pair(SirenAmberBg, SirenAmber)
                    SoundClass.BACKGROUND  -> Pair(if (isDark) DarkSurfaceVar else SurfaceVar, TextTertiary)
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(circleBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        currentDetection.soundClass.icon,
                        contentDescription = currentDetection.soundClass.label,
                        tint = circleIcon,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        currentDetection.soundClass.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        currentDetection.model.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${currentDetection.inferenceMs}ms inference",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
                // Confidence circle
                ConfidenceCircle(currentDetection.confidence)
            }

            Spacer(Modifier.height(14.dp))

            // Probability bars — live per-class scores from model
            // allScores order: [0]=Fire Alarm, [1]=Background, [2]=Siren
            val fireScore  = if (currentDetection.allScores.size > 0) currentDetection.allScores[0] else 0f
            val bgScore    = if (currentDetection.allScores.size > 1) currentDetection.allScores[1] else 0f
            val sirenScore = if (currentDetection.allScores.size > 2) currentDetection.allScores[2] else 0f

            ConfidenceBar("Fire Alarm", fireScore,  FireRed)
            Spacer(Modifier.height(6.dp))
            ConfidenceBar("Siren",      sirenScore, SirenAmber)
            Spacer(Modifier.height(6.dp))
            ConfidenceBar("Background", bgScore,    SafeGreen)
        }

        // ── Quick Actions ─────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onNavigate("monitor") },
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Live Monitor", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { onNavigate("history") },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("History", style = MaterialTheme.typography.labelLarge)
            }
        }

        // ── Recent Alerts ─────────────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title    = "Recent Alerts",
                action   = "View All",
                onAction = { onNavigate("history") }
            )
            Spacer(Modifier.height(8.dp))
            if (recentAlerts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No alerts yet — start monitoring to detect sounds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            } else {
                recentAlerts.forEachIndexed { i, record ->
                    AlertRow(record)
                    if (i < recentAlerts.size - 1) {
                        HorizontalDivider(color = if (isDark) DarkBorder else Border, thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Emergency Banner ─────────────────────────────────────────
@Composable
fun EmergencyBanner(detection: DetectionResult) {
    if (!detection.isEmergency) return
    val inf = rememberInfiniteTransition(label = "banner")
    val alpha by inf.animateFloat(
        1f, 0.6f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    val (bg, border, title) = when (detection.soundClass) {
        SoundClass.FIRE_ALARM -> Triple(FireRedBg,    FireRed,    "FIRE ALARM DETECTED")
        SoundClass.SIREN      -> Triple(SirenAmberBg, SirenAmber, "SIREN DETECTED")
        else                  -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            detection.soundClass.icon,
            contentDescription = detection.soundClass.label,
            tint = border,
            modifier = Modifier.size(32.dp).graphicsLayer(alpha = alpha)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = border)
            Text(
                "Confidence: ${(detection.confidence * 100).toInt()}% · ${detection.model.displayName} · ${detection.inferenceMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Icon(Icons.Default.Watch, null, tint = border, modifier = Modifier.size(18.dp))
    }
}

// ── Confidence circle ────────────────────────────────────────
@Composable
fun ConfidenceCircle(value: Float) {
    val anim by animateFloatAsState(value, tween(600), label = "conf")
    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { anim },
            modifier = Modifier.fillMaxSize(),
            color    = Primary,
            trackColor = Border,
            strokeWidth = 4.dp
        )
        Text(
            "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Alert row ────────────────────────────────────────────────
@Composable
fun AlertRow(record: AlertRecord) {
    val isDark = isSystemDark()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(
                    when (record.soundClass) {
                        SoundClass.FIRE_ALARM  -> FireRed
                        SoundClass.SIREN       -> SirenAmber
                        SoundClass.BACKGROUND  -> TextTertiary
                    }
                )
        )
        Column(Modifier.weight(1f)) {
            Text(record.soundClass.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(record.timestamp,        style = MaterialTheme.typography.labelSmall,  color = TextTertiary)
        }
        Text(
            "${(record.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = Primary,
            fontWeight = FontWeight.Bold
        )
    }
}
