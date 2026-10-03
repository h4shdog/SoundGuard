package com.soundguard.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundguard.app.data.SoundClass
import com.soundguard.app.ui.theme.*

// ─────────────────────────────────────────────────────────────
// SoundGuard Card
// ─────────────────────────────────────────────────────────────
@Composable
fun SgCard(
    modifier: Modifier = Modifier,
    topAccentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemDark()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) DarkSurface else Surface)
            .border(1.dp, if (isDark) DarkBorder else Border, RoundedCornerShape(16.dp))
            .then(
                if (topAccentColor != null) Modifier
                else Modifier
            )
    ) {
        if (topAccentColor != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(topAccentColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
        }
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ─────────────────────────────────────────────────────────────
// Stat Card
// ─────────────────────────────────────────────────────────────
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    value: String,
    label: String,
    trend: String,
    trendColor: Color,
    accentColor: Color
) {
    SgCard(modifier = modifier, topAccentColor = accentColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(label, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Text(trend, style = MaterialTheme.typography.labelSmall, color = trendColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, style = MaterialTheme.typography.labelMedium, color = Primary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Pulsing status dot
// ─────────────────────────────────────────────────────────────
@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val inf = rememberInfiniteTransition(label = "dot")
    val scale by inf.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "scale"
    )
    Box(
        Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

// ─────────────────────────────────────────────────────────────
// Confidence bar row
// ─────────────────────────────────────────────────────────────
@Composable
fun ConfidenceBar(
    label: String,
    value: Float,           // 0..1
    color: Color,
    modifier: Modifier = Modifier
) {
    val anim by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(600), label = "conf"
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        LinearProgressIndicator(
            progress = { anim },
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Border
        )
        Text(
            "${(value * 100).toInt()}%",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Sound class chip
// ─────────────────────────────────────────────────────────────
@Composable
fun SoundClassChip(soundClass: SoundClass) {
    val (bg, fg, text) = when (soundClass) {
        SoundClass.FIRE_ALARM  -> Triple(FireRedLight,    FireRed,    "🔥 Fire Alarm")
        SoundClass.SIREN       -> Triple(SirenAmberLight, SirenAmber, "🚨 Siren")
        SoundClass.BACKGROUND  -> Triple(Border,          TextSecondary, "🔊 Background")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────
// Inline helper — detect dark theme without CompositionLocal
// ─────────────────────────────────────────────────────────────
@Composable
fun isSystemDark(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()
