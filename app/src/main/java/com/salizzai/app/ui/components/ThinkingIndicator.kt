package com.salizzai.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.salizzai.app.ui.theme.SaliGradients
import kotlinx.coroutines.delay

/**
 * Shown in place of an assistant bubble that has not produced any visible text
 * yet. A pulsing brand dot plus an elapsed timer gives feedback during long
 * reasoning phases where nothing else can be displayed.
 */
@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val fade by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "fade"
    )

    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .scale(pulse)
                .background(SaliGradients.brand(MaterialTheme.colorScheme.background.isDark()), RoundedCornerShape(50))
        )
        Text(
            text = label ?: "در حال تفکر…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (seconds >= 2) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "${seconds} ثانیه",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(fade).padding(top = 1.dp)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.Color.isDark(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5f
