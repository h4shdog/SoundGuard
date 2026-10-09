package com.soundguard.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Full-screen black landing page shown once at app launch.
 * Fades in the logo + wordmark, holds briefly, then calls [onFinished]
 * to transition into the main navigation graph.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {

    // Animate the content alpha from 0 → 1 over 600 ms
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        delay(1_000L)   // hold the logo visible for 1 second after fade-in
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier             = Modifier.alpha(alpha.value),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.spacedBy(20.dp)
        ) {
            // Logo box — mirrors the top-bar logo in MainNavigation
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.GraphicEq,
                    contentDescription = "Hearmergency logo",
                    tint               = Color.White,
                    modifier           = Modifier.size(52.dp)
                )
            }

            // App name
            Text(
                text       = "Hearmergency",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White
            )

            // Tagline
            Text(
                text  = "Fire Alarm & Siren Detection",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
