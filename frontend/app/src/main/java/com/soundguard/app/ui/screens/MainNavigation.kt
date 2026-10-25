package com.soundguard.app.ui.screens

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.soundguard.app.data.AlertRepository
import com.soundguard.app.service.SoundMonitorService
import com.soundguard.app.ui.components.isSystemDark
import com.soundguard.app.ui.theme.*
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.BrightnessAuto

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Monitor   : Screen("monitor",   "Monitor",   Icons.Default.Mic)
    object History   : Screen("history",   "History",   Icons.Default.History)
    object Settings  : Screen("settings",  "Settings",  Icons.Default.Settings)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Monitor,
    Screen.History,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(themeViewModel: ThemeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val navController = rememberNavController()
    val isDark        = isSystemDark()
    val context       = LocalContext.current
    val isRunning     = AlertRepository.isRunning

    fun startService() {
        context.startForegroundService(
            Intent(context, SoundMonitorService::class.java)
                .setAction(SoundMonitorService.ACTION_START)
        )
    }

    fun stopService() {
        context.startService(
            Intent(context, SoundMonitorService::class.java)
                .setAction(SoundMonitorService.ACTION_STOP)
        )
    }

    Scaffold(
        containerColor = if (isDark) DarkBackground else Background,
        topBar = {
            // ── App top bar ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Logo box
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = androidx.compose.ui.res.painterResource(id = com.soundguard.app.R.drawable.ic_hearmergency_logo),
                        contentDescription = "Hearmergency",
                        modifier           = Modifier.size(40.dp)
                    )
                }
                Column {
                    Text(
                        "Hearmergency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        "Fire Alarm and Siren Detection",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Push toggle to the far right
                Spacer(Modifier.weight(1f))

                // Theme toggle button
                val (themeIcon, themeDesc) = when (themeViewModel.themeMode) {
                    ThemeMode.LIGHT  -> Icons.Default.LightMode  to "Switch to Dark Mode"
                    ThemeMode.DARK   -> Icons.Default.DarkMode   to "Switch to System Mode"
                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto to "Switch to Light Mode"
                }
                IconButton(onClick = { themeViewModel.toggleTheme() }) {
                    Icon(
                        imageVector        = themeIcon,
                        contentDescription = themeDesc,
                        tint               = Color.White,
                        modifier           = Modifier.size(22.dp)
                    )
                }
            }
        },
        bottomBar = {
            // ── Bottom nav with centred FAB mic ──────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) DarkSurface else Surface)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(
                    color = if (isDark) DarkBorder else Border,
                    thickness = 0.5.dp
                )

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDest       = navBackStackEntry?.destination
                val monitorSelected   = currentDest?.hierarchy?.any { it.route == Screen.Monitor.route } == true
                val historySelected   = currentDest?.hierarchy?.any { it.route == Screen.History.route } == true

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left — Monitor tab
                    NavTabItem(
                        icon = Icons.Default.GraphicEq,
                        label = "Monitor",
                        selected = monitorSelected,
                        modifier = Modifier.weight(1f)
                    ) {
                        navController.navigate(Screen.Monitor.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }

                    // Centre — Mic FAB
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulse rings (only when recording)
                        if (isRunning) {
                            val inf = rememberInfiniteTransition(label = "micPulse")
                            val pulseScale by inf.animateFloat(
                                initialValue = 1f,
                                targetValue  = 1.55f,
                                animationSpec = infiniteRepeatable(
                                    tween(900, easing = FastOutSlowInEasing),
                                    RepeatMode.Restart
                                ),
                                label = "pulseScale"
                            )
                            val pulseAlpha by inf.animateFloat(
                                initialValue = 0.45f,
                                targetValue  = 0f,
                                animationSpec = infiniteRepeatable(
                                    tween(900, easing = FastOutSlowInEasing),
                                    RepeatMode.Restart
                                ),
                                label = "pulseAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .offset(y = (-8).dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = pulseAlpha))
                            )
                        }

                        // FAB button
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .offset(y = (-8).dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (isRunning) stopService() else startService()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isRunning) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = if (isRunning) "Stop Monitoring" else "Start Monitoring",
                                tint = if (isRunning) Color.White else Color(0xFFEF4444),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Right — History tab
                    NavTabItem(
                        icon = Icons.Default.History,
                        label = "History",
                        selected = historySelected,
                        modifier = Modifier.weight(1f)
                    ) {
                        navController.navigate(Screen.History.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Monitor.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                })
            }
            composable(Screen.Monitor.route)  { MonitorScreen() }
            composable(Screen.History.route)  { HistoryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun NavTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = isSystemDark()
    val selectedColor   = if (isDark) Color.White        else Color.Black
    val unselectedColor = if (isDark) Color(0xFF94A3B8)  else TextTertiary

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) selectedColor else unselectedColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) selectedColor else unselectedColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
