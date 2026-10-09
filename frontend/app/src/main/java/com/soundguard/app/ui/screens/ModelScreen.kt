package com.soundguard.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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

@Composable
fun ModelScreen() {
    val isDark  = isSystemDark()
    val bg      = if (isDark) DarkBackground else Background
    val metrics = DemoData.bestModelMetrics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Model Info", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "EfficientNetB0 · Log-Mel Spectrogram · 3-Class",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )

        // ── Model Identity Card ───────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth(), topAccentColor = Primary) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Psychology, null, tint = Primary, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        ModelType.BEST_MODEL.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        ModelType.BEST_MODEL.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SafeGreenBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Active", style = MaterialTheme.typography.labelSmall, color = SafeGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Performance Metrics ───────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth(), topAccentColor = Primary) {
            Text("Performance Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            MetricRow("Accuracy",  metrics.accuracy,  Primary)
            Spacer(Modifier.height(8.dp))
            MetricRow("Precision", metrics.precision, Primary)
            Spacer(Modifier.height(8.dp))
            MetricRow("Recall",    metrics.recall,    Primary)
            Spacer(Modifier.height(8.dp))
            MetricRow("F1-Score",  metrics.f1,        Primary)
            Spacer(Modifier.height(8.dp))
            InferenceRow(metrics.inferenceMs, SafeGreen)
        }

        // ── Spectrogram Params ────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Text("Log-Mel Spectrogram Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            val params = listOf(
                // Values from DataPreprocessing_Hashiras training notebook
                "Sample Rate"    to "16 kHz",
                "FFT Window"     to "1024",
                "Hop Length"     to "512",
                "Mel Freq. Bins" to "128",
                "Freq. Min"      to "50 Hz",
                "Freq. Max"      to "8000 Hz",
                "Log Compress"   to "power_to_db (ref=max)",
                "Window Type"    to "Hann",
                "Input Size"     to "224 × 224 × 3",
                "Normalization"  to "librosa.util.normalize"
            )
            params.forEachIndexed { i, (k, v) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Primary)
                }
                if (i < params.size - 1) HorizontalDivider(color = if (isDark) DarkBorder else Border, thickness = 0.5.dp)
            }
        }

        // ── Sound Categories ──────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Text("Sound Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SoundClass.entries.forEach { sc ->
                val (bg2, fg) = when (sc) {
                    SoundClass.FIRE_ALARM  -> Pair(FireRedLight,    FireRed)
                    SoundClass.SIREN       -> Pair(SirenAmberLight, SirenAmber)
                    SoundClass.BACKGROUND  -> Pair(if (isDark) DarkSurfaceVar else SurfaceVar, TextSecondary)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg2)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(fg.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(sc.icon, contentDescription = sc.label, tint = fg, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sc.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg)
                        Text("Label: ${sc.dbLabel}", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── Dataset Summary ───────────────────────────────
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Text("Training Dataset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            // Numbers from DataPreprocessing_Hashiras training notebook
            val splits = listOf(
                Triple("Train",      "2,762 samples", "fire_alarm: 695 · noise: 1372 · siren: 695"),
                Triple("Validation", "504 samples",   "fire_alarm: 61  · noise: 294  · siren: 149"),
                Triple("Test",       "506 samples",   "fire_alarm: 62  · noise: 294  · siren: 150")
            )
            splits.forEachIndexed { i, (split, count, detail) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(split, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(detail, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                    Text(count, style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Bold)
                }
                if (i < splits.size - 1) HorizontalDivider(color = if (isDark) DarkBorder else Border, thickness = 0.5.dp)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) DarkSurfaceVar else SurfaceVar)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(16.dp))
                Text(
                    "Fire alarm class was augmented (Shift, PitchShift, TimeStretch, GaussianNoise) to balance with siren samples.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun MetricRow(label: String, value: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text(
                "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Border
        )
    }
}

@Composable
fun InferenceRow(ms: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Inference", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SafeGreenBg)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("${ms}ms", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
