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

    private var showRationale = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            startMonitoringService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoundGuardTheme {
                MainNavigation()

                // Rationale dialog — shown before the OS permission prompt
                if (showRationale.value) {
                    PermissionRationaleDialog(
                        onContinue = {
                            showRationale.value = false
                            permissionLauncher.launch(requiredPermissions)
                        }
                    )
                }
            }
        }

        if (hasMicPermission()) {
            startMonitoringService()
        } else {
            // Show rationale first, then trigger the OS dialog
            showRationale.value = true
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun startMonitoringService() {
        val intent = Intent(this, SoundMonitorService::class.java)
            .setAction(SoundMonitorService.ACTION_START)
        startForegroundService(intent)
    }
}

// ── Rationale dialog ──────────────────────────────────────────
@Composable
private fun PermissionRationaleDialog(onContinue: () -> Unit) {
    Dialog(onDismissRequest = {}) {   // non-dismissable — permission is required
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 4.dp
        ) {
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
                    "SoundGuard needs continuous microphone access to detect fire alarms and sirens — even when you're not using the app.\n\n" +
                    "On the next screen, please select \"While using the app\". " +
                    "The app runs a background service with a notification, so monitoring stays active at all times.",
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.runtime.Composable
fun MainPreview() {
    SoundGuardTheme {
        MainNavigation()
    }
}
