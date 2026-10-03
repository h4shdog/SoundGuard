package com.soundguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary          = Primary,
    onPrimary        = Color.White,
    primaryContainer = PrimaryLight,
    secondary        = SafeGreen,
    onSecondary      = Color.White,
    error            = FireRed,
    background       = Background,
    surface          = Surface,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    outline          = Border
)

private val DarkColors = darkColorScheme(
    primary          = PrimaryLight,
    onPrimary        = Color.White,
    primaryContainer = PrimaryDark,
    secondary        = SafeGreen,
    onSecondary      = Color.White,
    error            = FireRed,
    background       = DarkBackground,
    surface          = DarkSurface,
    onBackground     = Color(0xFFF1F5F9),
    onSurface        = Color(0xFFF1F5F9),
    outline          = DarkBorder
)

@Composable
fun SoundGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = SoundGuardTypography,
        content     = content
    )
}
