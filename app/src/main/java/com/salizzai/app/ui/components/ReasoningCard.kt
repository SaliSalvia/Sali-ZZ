package com.salizzai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salizzai.app.ui.theme.SaliGradients
import kotlinx.coroutines.delay

/**
 * Collapsible "deep thinking" card shown above an answer.
 *
 * While the model is still reasoning the header shows a live timer plus a subtle
 * progress shimmer and the body follows the stream; once the answer starts the
 * card folds itself away so the reply gets the space (DeepSeek-style), and the
 * user can re-open it any time to read the full chain of thought.
 */
@Composable
fun ReasoningCard(
    reasoning: String,
    durationMs: Long,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    defaultExpanded: Boolean? = null
) {
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    var expanded by rememberSaveable(reasoning.length > 0, isStreaming) {
        mutableStateOf(defaultExpanded ?: isStreaming)
    }
    // Once the answer lands, fold the panel so the response is fully visible.
    LaunchedEffect(isStreaming) {
        if (!isStreaming && defaultExpanded == null) expanded = false
    }

    // Live elapsed time while thinking. Keyed on isStreaming only: keying on the
    // reasoning length would restart this coroutine on every streamed token.
    var elapsed by remember { mutableStateOf(durationMs) }
    LaunchedEffect(isStreaming, durationMs) {
        if (!isStreaming) {
            elapsed = durationMs
            return@LaunchedEffect
        }
        var ticks = 0L
        while (true) {
            delay(100)
            ticks += 100
            elapsed = durationMs + ticks
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "chevron"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(SaliGradients.reasoning(dark))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStreaming) "در حال تفکر…" else "روند تفکر",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDuration(elapsed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (expanded) "بستن" else "نمایش",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation)
                )
            }

            if (isStreaming && !expanded) {
                Spacer(modifier = Modifier.size(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 2.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(200)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = contentModifier
                        .padding(top = 8.dp)
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = reasoning.ifBlank { "…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isStreaming) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Row(horizontalArrangement = Arrangement.Start) {
                            TypingDots()
                        }
                    }
                }
            }
        }
    }
}

/** `1.2s` / `41s` / `2m 05s` — compact enough for a chip. */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSeconds = ms / 1000.0
    return when {
        totalSeconds < 1 -> "<1 ثانیه"
        totalSeconds < 60 -> "%.1f ثانیه".format(totalSeconds)
        else -> {
            val minutes = (totalSeconds / 60).toInt()
            val seconds = (totalSeconds % 60).toInt()
            "%d:%02d دقیقه".format(minutes, seconds)
        }
    }
}

@Composable
internal fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, delayMillis = index * 130),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

private fun androidx.compose.ui.graphics.Color.luminanceIsDark(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5f
