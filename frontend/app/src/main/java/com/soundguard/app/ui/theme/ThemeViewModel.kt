package com.soundguard.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ThemeViewModel : ViewModel() {
    var themeMode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun toggleTheme() {
        themeMode = when (themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT  -> ThemeMode.DARK
            ThemeMode.DARK   -> ThemeMode.SYSTEM
        }
    }
}
