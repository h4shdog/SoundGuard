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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.soundguard.app.data.ModelType
import com.soundguard.app.ui.components.*
import com.soundguard.app.ui.theme.*

@Composable
fun SettingsScreen() {
    val isDark = isSystemDark()
    val bg     = if (isDark) DarkBackground else Background

    // State
    var vibrationOn  by remember { mutableStateOf(true) }
    var flashOn      by remember { mutableStateOf(true) }
    var watchSyncOn  by remember { mutableStateOf(true) }
    var notifOn      by remember { mutableStateOf(false) }
    var deviceName   by remember { mutableStateOf("SoundGuard Watch v1") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)

        // ── Model Configuration ───────────────────────────
        SettingsSection(title = "Model Configuration", icon = Icons.Default.Psychology) {

            // Active model (single, fixed)
            Text("Active Model", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Primary.copy(alpha = 0.1f))
                    .border(1.dp, Primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Psychology, null, tint = Primary, modifier = Modifier.size(18.dp))
                Text(
                    ModelType.BEST_MODEL.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ModelType.BEST_MODEL.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        // ── Alert Configuration ───────────────────────────
        SettingsSection(title = "Alert Configuration", icon = Icons.Default.NotificationsActive) {
            listOf(
                Triple(Icons.Default.Vibration,     "Vibration",        vibrationOn),
                Triple(Icons.Default.FlashOn,        "Visual Flash",     flashOn),
                Triple(Icons.Default.Watch,          "Watch Companion",  watchSyncOn),
                Triple(Icons.Default.Notifications,  "Screen Alert",     notifOn)
            ).forEachIndexed { i, (icon, label, state) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state,
                        onCheckedChange = {
                            when (i) {
                                0 -> vibrationOn = it
                                1 -> flashOn     = it
                                2 -> watchSyncOn = it
                                3 -> notifOn     = it
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                    )
                }
                if (i < 3) HorizontalDivider(color = if (isDark) DarkBorder else Border, thickness = 0.5.dp)
            }
        }

        // ── Device & Watch ────────────────────────────────
        SettingsSection(title = "Device & Watch", icon = Icons.Default.Watch) {
            Text("Device Name", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
            OutlinedTextField(
                value         = deviceName,
                onValueChange = { deviceName = it },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                textStyle     = MaterialTheme.typography.bodyMedium,
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Primary,
                    unfocusedBorderColor = Border
                )
            )
            Spacer(Modifier.height(8.dp))
            // Watch paired status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SafeGreenBg)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Watch, null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wear OS XL Round", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SafeGreen)
                    Text("API 37 · 480×480 · Connected", style = MaterialTheme.typography.labelSmall, color = SafeGreen.copy(alpha = 0.7f))
                }
                Box(
                    Modifier.size(8.dp)
                        .clip(CircleShape)
                        .background(SafeGreen)
                )
            }
        }

        // ── Save Button ───────────────────────────────────
        Button(
            onClick  = { /* TODO: persist settings */ },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save Settings", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Settings section card ──────────────────────────────────────
@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemDark()
    SgCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = if (isDark) DarkBorder else Border)
        content()
    }
}

