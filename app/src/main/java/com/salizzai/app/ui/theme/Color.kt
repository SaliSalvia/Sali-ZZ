package com.salizzai.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ─── Brand palette ──────────────────────────────────────────────────
// Cosmic violet core, cyan highlights and a restrained warm gold accent.

val SaliPurple = Color(0xFF6C4BE0)
val SaliPurpleDark = Color(0xFF2A1F4D)
val SaliPurpleLight = Color(0xFF9E86FF)
val SaliPurpleAccent = Color(0xFF4FD8E8)
val SaliGold = Color(0xFFE0BC7A)

// ─── Dark theme ─────────────────────────────────────────────────────
val DarkBackground = Color(0xFF08070D)
val DarkSurface = Color(0xFF12101A)
val DarkSurfaceElevated = Color(0xFF191622)
val DarkSurfaceVariant = Color(0xFF1E1B29)
val DarkOnBackground = Color(0xFFF2EFF7)
val DarkOnSurface = Color(0xFFF7F4FB)
val DarkOnSurfaceVariant = Color(0xFFA9A3B8)
val DarkOutline = Color(0xFF342E45)
val DarkOutlineVariant = Color(0xFF272233)

// ─── Light theme ────────────────────────────────────────────────────
val LightBackground = Color(0xFFF7F5F2)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFEAF6)
val LightOnBackground = Color(0xFF17141F)
val LightOnSurface = Color(0xFF17141F)
val LightOnSurfaceVariant = Color(0xFF615B6E)
val LightOutline = Color(0xFFD8D1E4)
val LightOutlineVariant = Color(0xFFE7E1F0)

// ─── Chat bubbles ───────────────────────────────────────────────────
val UserBubbleDark = Color(0xFF3B2A6B)
val AssistantBubbleDark = Color(0xFF16131F)
val UserBubbleLight = Color(0xFF6C4BE0)
val AssistantBubbleLight = Color(0xFFF3F0FA)

// ─── Status ─────────────────────────────────────────────────────────
val SuccessGreen = Color(0xFF2FD08A)
val ErrorRed = Color(0xFFFF5C6C)
val WarningAmber = Color(0xFFF5A524)
val InfoBlue = Color(0xFF4C8DFF)

// ─── Gradients ──────────────────────────────────────────────────────
object SaliGradients {
    /** Brand gradient used for the send button, logo chip and highlights. */
    fun brand(dark: Boolean): Brush = Brush.linearGradient(
        colors = if (dark) {
            listOf(Color(0xFF7C5CFF), Color(0xFF4FD8E8))
        } else {
            listOf(Color(0xFF6C4BE0), Color(0xFF3FB6D6))
        }
    )

    /** User bubble gradient — subtle depth without breaking readability. */
    fun userBubble(dark: Boolean): Brush = Brush.linearGradient(
        colors = if (dark) {
            listOf(Color(0xFF4A3282), Color(0xFF35275F))
        } else {
            listOf(Color(0xFF7B5CEA), Color(0xFF5B3ED0))
        }
    )

    /** Soft glow behind the reasoning panel. */
    fun reasoning(dark: Boolean): Brush = Brush.linearGradient(
        colors = if (dark) {
            listOf(Color(0x1F9E86FF), Color(0x0D4FD8E8))
        } else {
            listOf(Color(0x149E86FF), Color(0x0A4FD8E8))
        }
    )
}
