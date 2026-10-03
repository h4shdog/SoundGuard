package com.soundguard.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.soundguard.app.ui.components.isSystemDark
import com.soundguard.app.ui.theme.*

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
fun MainNavigation() {
    val navController = rememberNavController()
    val isDark        = isSystemDark()

    Scaffold(
        containerColor = if (isDark) DarkBackground else Background,
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) DarkSurface else Surface,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDest = navBackStackEntry?.destination

                bottomNavScreens.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(screen.label, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor       = Primary,
                            selectedTextColor       = Primary,
                            indicatorColor          = Primary.copy(alpha = 0.12f),
                            unselectedIconColor     = TextTertiary,
                            unselectedTextColor     = TextTertiary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Dashboard.route,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = { fadeIn() + slideInHorizontally { it / 10 } },
            exitTransition   = { fadeOut() }
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(onNavigate = { navController.navigate(it) }) }
            composable(Screen.Monitor.route)   { MonitorScreen()   }
            composable(Screen.History.route)   { HistoryScreen()   }
            composable(Screen.Settings.route)  { SettingsScreen()  }
        }
    }
}
