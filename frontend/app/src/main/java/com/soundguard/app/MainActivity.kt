package com.soundguard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.soundguard.app.service.SoundMonitorService
import com.soundguard.app.ui.screens.MainNavigation
import com.soundguard.app.ui.theme.Primary
import com.soundguard.app.ui.theme.SoundGuardTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    // null  = not yet asked
    // true  = granted
    // false = denied
    private var permissionState = mutableStateOf<Boolean?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        permissionState.value = micGranted
        if (micGranted) {
            startMonitoringService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check permission state without triggering any prompt yet
        permissionState.value = if (hasMicPermission()) true else null

        setContent {
            SoundGuardTheme {
                MainNavigation()

                when (permissionState.value) {
                    null -> {
                        // First launch: show our rationale dialog, then trigger OS dialog
                        PermissionRationaleDialog(
                            onContinue = {
                                permissionState.value = false  // updated by launcher callback
                                permissionLauncher.launch(requiredPermissions)
                            }
                        )
                    }
                    false -> {
                        // User denied: show non-dismissable explanation and retry button
                        PermissionDeniedDialog(
                            onRetry = { permissionLauncher.launch(requiredPermissions) }
                        )
                    }
                    true -> { /* granted — nothing to show */ }
                }
            }
        }

        // Permission was already granted on a subsequent launch — start service immediately
        if (hasMicPermission()) {
            startMonitoringService()
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun startMonitoringService() {
        startForegroundService(
            Intent(this, SoundMonitorService::class.java)
                .setAction(SoundMonitorService.ACTION_START)
        )
    }
}

// ── Permission rationale dialog ───────────────────────────────
@Composable
private fun PermissionRationaleDialog(onContinue: () -> Unit) {
    Dialog(onDismissRequest = {}) {   // non-dismissable — permission is required
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Microphone Access Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "SoundGuard needs continuous microphone access to detect fire alarms " +
                    "and sirens in real time — even when you are not actively using the app.\n\n" +
                    "On the next screen, please select \"While using the app\". " +
                    "A persistent notification will appear while monitoring is active.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(
                        "Grant Microphone Access",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Permission denied dialog ──────────────────────────────────
@Composable
private fun PermissionDeniedDialog(onRetry: () -> Unit) {
    Dialog(onDismissRequest = {}) {   // non-dismissable
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🎤", fontSize = 40.sp)
                Text(
                    "Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "SoundGuard cannot function without microphone access. " +
                    "This app is designed to help people who are deaf or hard of hearing " +
                    "by detecting fire alarms and sirens and notifying them on their phone " +
                    "or watch.\n\nPlease grant microphone permission to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(
                        "Try Again",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.runtime.Composable
fun MainPreview() {
    SoundGuardTheme { MainNavigation() }
}
