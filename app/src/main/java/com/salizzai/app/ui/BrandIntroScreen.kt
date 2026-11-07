package com.salizzai.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.salizzai.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BrandIntroScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.88f) }
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(420, easing = FastOutSlowInEasing)
            )
        }
        launch {
            glowAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
            delay(220)
            glowAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(240, easing = FastOutLinearInEasing)
            )
        }

        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        )
        delay(200)
        logoAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(240, easing = FastOutLinearInEasing)
        )
        delay(70)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(350.dp)
                .graphicsLayer { alpha = glowAlpha.value }
        ) {
            val radius = size.minDimension * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x665E4AE8),
                        Color(0x334ACBEA),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )
            drawCircle(
                color = Color(0x45D7B56D),
                radius = radius * 0.72f,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Image(
            painter = painterResource(R.drawable.sali_logo_splash),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                }
        )
    }
}
