package com.soundguard.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.soundguard.app.R
import kotlinx.coroutines.delay

/**
 * Full-screen black landing page shown once at app launch.
 * Renders the Hearmergency logo PNG (ear + pulse + wordmark),
 * fades in over 700 ms, holds for 1 s, then calls [onFinished].
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        delay(1_000L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter            = painterResource(id = R.drawable.ic_hearmergency_logo),
            contentDescription = "Hearmergency logo",
            modifier           = Modifier
                .alpha(alpha.value)
                .size(280.dp)
        )
    }
}
