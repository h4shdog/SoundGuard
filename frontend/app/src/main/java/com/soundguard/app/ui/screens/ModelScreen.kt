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
            "BestModel · Log-Mel Spectrogram",
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
                "Sample Rate"    to "16 kHz",
                "FFT Window"     to "2048",
                "Hop Length"     to "512",
                "Mel Freq. Bins" to "128",
                "Input Size"     to "224 × 224 × 3",
                "Window Type"    to "Hamming",
                "Log Compress"   to "log1p",
                "Normalization"  to "[-1, 1]"
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
                    Text(sc.emoji, fontSize = 22.sp)
                    Column(Modifier.weight(1f)) {
                        Text(sc.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg)
                        Text("Label: ${sc.dbLabel}", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(6.dp))
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
