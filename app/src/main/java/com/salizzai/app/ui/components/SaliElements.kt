package com.salizzai.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.salizzai.app.ui.theme.SaliGradients

/**
 * Shared "brand kit" pieces used by the chat, welcome and navigation surfaces.
 *
 * Everything here is intentionally dependency-free (no blur, no third-party
 * shimmer library) so it renders identically from API 26 up and costs only a
 * few draw calls per frame.
 */

private fun Color.isDarkColor(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5f

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.isDarkColor()

/** Small squared-off brand mark: gradient tile with the Z monogram. */
@Composable
fun SaliBrandAvatar(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(SaliGradients.brand(isDarkTheme())),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Z",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Ring-shaped brand orb used on the empty chat screen. It rotates a sweep
 * gradient under a punched-out center, which reads as a slowly turning halo
 * instead of a flat logo.
 */
@Composable
fun BrandOrb(
    size: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "brand-orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "brand-orb-rotation"
    )
    val dark = isDarkTheme()
    val sweep = Brush.sweepGradient(
        if (dark) {
            listOf(Color(0xFF7C5CFF), Color(0xFF4FD8E8), Color(0xFFE0BC7A), Color(0xFF7C5CFF))
        } else {
            listOf(Color(0xFF6C4BE0), Color(0xFF3FB6D6), Color(0xFFB08A3E), Color(0xFF6C4BE0))
        }
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Soft halo so the orb sits on the background instead of floating.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.28f }
                .clip(CircleShape)
                .background(sweep)
        )
        Box(
            modifier = Modifier
                .size(size * 0.96f)
                .graphicsLayer { rotationZ = rotation }
                .clip(CircleShape)
                .background(sweep)
        )
        Box(
            modifier = Modifier
                .size(size * 0.74f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
        )
        Text(
            text = "Z",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Animated gradient hairline. Idle it is a plain neutral border, while
 * [active] keeps a cyan highlight running around the shape — used to show a
 * reply that is still streaming.
 */
@Composable
fun Modifier.liveBorder(
    active: Boolean,
    shape: Shape,
    width: Dp = 1.2.dp
): Modifier {
    if (!active) {
        return border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    val transition = rememberInfiniteTransition(label = "live-border")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "live-border-shift"
    )
    val dark = isDarkTheme()
    val brush = Brush.linearGradient(
        colors = if (dark) {
            listOf(
                Color(0x337C5CFF), Color(0xFF7C5CFF), Color(0xFF4FD8E8), Color(0x337C5CFF)
            )
        } else {
            listOf(
                Color(0x336C4BE0), Color(0xFF6C4BE0), Color(0xFF3FB6D6), Color(0x336C4BE0)
            )
        },
        start = Offset(-520f + shift * 1040f, 0f),
        end = Offset(120f + shift * 1040f, 520f)
    )
    return border(width, brush, shape)
}

/** Expanding ring behind a button; silent no-op when [active] is false. */
@Composable
fun Modifier.pulseHalo(
    active: Boolean,
    color: Color
): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "pulse-halo")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse-halo-progress"
    )
    return drawBehind {
        val maxRadius = size.minDimension / 2f
        drawCircle(
            color = color.copy(alpha = (1f - progress) * 0.30f),
            radius = maxRadius * (1f + progress * 0.7f)
        )
    }
}

/** Soft top-edge glow used behind headers so the bar never looks flat. */
@Composable
fun Modifier.topGlow(color: Color = MaterialTheme.colorScheme.primary): Modifier =
    drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.10f), Color.Transparent),
                startY = 0f,
                endY = size.height * 1.4f
            )
        )
    }

/**
 * Shared header for the secondary screens: the same top glow, transparent app
 * bar and hairline the chat screen uses, so switching tabs never loses the
 * brand treatment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaliTopBar(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column {
        Box(modifier = Modifier.topGlow()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

/**
 * Rounded tile holding a section or list icon. Brand-filled (`gradient`) for
 * primary marks, tinted for repeated section headers.
 */
@Composable
fun SaliIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color? = null,
    gradient: Boolean = false
) {
    val color = tint ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(
                if (gradient) SaliGradients.brand(isDarkTheme())
                else SolidColor(color.copy(alpha = 0.14f))
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (gradient) Color.White else color,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

/** Centered empty / placeholder block shared by the list screens. */
@Composable
fun SaliEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    orb: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (orb) {
            BrandOrb(size = 84.dp)
        } else {
            SaliIconTile(icon = icon, size = 52.dp, gradient = true)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Filled brand-gradient action button with an optional spinner state. */
@Composable
fun SaliGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val active = enabled && !loading
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(
                if (enabled) SaliGradients.brand(isDarkTheme())
                else SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 18.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
