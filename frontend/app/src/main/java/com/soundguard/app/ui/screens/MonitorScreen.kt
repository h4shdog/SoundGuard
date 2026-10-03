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
import androidx.compose.ui.unit.*
import com.soundguard.app.data.*
import com.soundguard.app.service.SoundMonitorService
import com.soundguard.app.ui.components.*
import com.soundguard.app.ui.theme.*@Composable
fun MonitorScreen() {
    val isDark      = isSystemDark()
    val bg          = if (isDark) DarkBackground else Background
    val context     = LocalContext.current
    val activeModel = ModelType.BEST_MODEL

    // Observe repository state — recompose whenever they change
    val isRunning by remember { derivedStateOf { AlertRepository.isRunning } }
    val detection by remember { derivedStateOf { AlertRepository.current } }

    fun startService() {
        val intent = Intent(context, SoundMonitorService::class.java)
            .setAction(SoundMonitorService.ACTION_START)
        context.startForegroundService(intent)
    }

    fun stopService() {
        val intent = Intent(context, SoundMonitorService::class.java)
            .setAction(SoundMonitorService.ACTION_STOP)
        context.startService(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Title ──────────────────────────────────────────
        Text(
            "Live Monitor",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Real-time emergency sound classification",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )

        // ── Active Model Badge ─────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Text("Active Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Primary.copy(alpha = 0.1f))
                    .border(1.dp, Primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Psychology, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(
                        activeModel.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        activeModel.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }

        // ── Detection Card ────────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Text("Detection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val det = AlertRepository.current   // read directly for latest value
            if (isRunning && det != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DetectionRing(det)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    det.soundClass.label,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (det.isEmergency) {
                                        PulsingDot(
                                            when (det.soundClass) {
                                                SoundClass.FIRE_ALARM -> FireRed
                                                SoundClass.SIREN      -> SirenAmber
                                                else                  -> SafeGreen
                                            }
                                        )
                                        Text(
                                            "ALERT",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = when (det.soundClass) {
                                                SoundClass.FIRE_ALARM -> FireRed
                                                SoundClass.SIREN      -> SirenAmber
                                                else                  -> SafeGreen
                                            },
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    } else {
                                        PulsingDot(SafeGreen)
                                        Text(
                                            "Monitoring",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SafeGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Text(
                                    "${(det.confidence * 100).toInt()}% · ${det.inferenceMs}ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Real per-class scores — allScores order: [0]=Fire Alarm, [1]=Background, [2]=Siren
                val fireScore  = if (det.allScores.size > 0) det.allScores[0] else 0f
                val bgScore    = if (det.allScores.size > 1) det.allScores[1] else 0f
                val sirenScore = if (det.allScores.size > 2) det.allScores[2] else 0f

                ConfidenceBar("Fire Alarm",  fireScore,  FireRed)
                Spacer(Modifier.height(6.dp))
                ConfidenceBar("Siren",       sirenScore, SirenAmber)
                Spacer(Modifier.height(6.dp))
                ConfidenceBar("Background",  bgScore,    SafeGreen)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Mic, null, modifier = Modifier.size(48.dp), tint = TextTertiary)
                        Text(
                            if (isRunning) "Waiting for first detection…" else "Tap Start to begin monitoring",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        // ── Controls ──────────────────────────────────────
        Button(
            onClick = { if (isRunning) stopService() else startService() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) FireRed else Primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (isRunning) "Stop Monitoring" else "Start Monitoring",
                style = MaterialTheme.typography.labelLarge
            )
        }

        // ── Background monitoring note ─────────────────────
        if (isRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SafeGreenBg)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FiberManualRecord, null, tint = SafeGreen, modifier = Modifier.size(10.dp))
                Text(
                    "Monitoring continues in the background when you leave the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafeGreen
                )
            }
        }

        // ── Alert Modes ────────────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Text("Alert Modes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            val modes = remember {
                mutableStateListOf(
                    Triple(Icons.Default.Vibration,     "Vibration",       true),
                    Triple(Icons.Default.FlashOn,       "Visual Flash",    true),
                    Triple(Icons.Default.Watch,         "Watch Companion", true),
                    Triple(Icons.Default.Notifications, "Screen Alert",    false)
                )
            }
            modes.forEachIndexed { i, (icon, label, checked) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = checked,
                        onCheckedChange = { modes[i] = Triple(icon, label, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                    )
                }
                if (i < modes.size - 1) HorizontalDivider(color = if (isDark) DarkBorder else Border, thickness = 0.5.dp)
            }
        }

        // ── Watch sync note ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEBF0FF))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Watch, null, tint = Primary, modifier = Modifier.size(18.dp))
            Text(
                "Emergency alerts will also be sent to your paired Wear OS watch.",
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryDark
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Animated detection ring ──────────────────────────────────
@Composable
fun DetectionRing(detection: DetectionResult) {
    val color = when (detection.soundClass) {
        SoundClass.FIRE_ALARM -> FireRed
        SoundClass.SIREN      -> SirenAmber
        SoundClass.BACKGROUND -> SafeGreen
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
            .size(90.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .border(3.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(detection.soundClass.emoji, fontSize = 34.sp)
    }
}
