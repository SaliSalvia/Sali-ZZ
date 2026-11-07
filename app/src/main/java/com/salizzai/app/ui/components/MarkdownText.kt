package com.salizzai.app.ui.components

import android.os.SystemClock
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

/**
 * Compose wrapper for the Markwon markdown renderer.
 *
 * Rendering markdown is expensive (it re-parses the whole document and rebuilds
 * every span). During a streaming answer the text changes many times per second,
 * so this wrapper throttles re-renders to ~12 fps while [streaming] is true and
 * always paints the final text once the stream stops. That was the main source
 * of scroll jank in long answers.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    textSizeSp: Float = 14.5f
) {
    val context = LocalContext.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary

    // One Markwon instance per (theme colour) — rebuilding it per message was wasteful.
    val markwon = remember(onSurface, linkColor) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .build()
    }

    val argb = remember(onSurface) { onSurface.toArgbCompat() }
    val linkArgb = remember(linkColor) { linkColor.toArgbCompat() }
    @Suppress("DEPRECATION")
    val lastPaint = remember { longArrayOf(0L) }
    val lastText = remember { arrayOfNulls<String>(1) }

    AndroidView(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(argb)
                setLinkTextColor(linkArgb)
                textSize = textSizeSp
                setLineSpacing(0f, 1.25f)
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
                markwon.setMarkdown(this, markdown)
                lastText[0] = markdown
                lastPaint[0] = SystemClock.uptimeMillis()
            }
        },
        update = { tv ->
            tv.setTextColor(argb)
            tv.setLinkTextColor(linkArgb)
            if (tv.textSize != textSizeSp) tv.textSize = textSizeSp
            if (lastText[0] == markdown) return@AndroidView
            val now = SystemClock.uptimeMillis()
            // Throttle while streaming: at most one re-parse every ~85 ms.
            if (streaming && now - lastPaint[0] < 85L) return@AndroidView
            lastPaint[0] = now
            lastText[0] = markdown
            markwon.setMarkdown(tv, markdown)
            Linkify.addLinks(tv, Linkify.WEB_URLS)
        },
        modifier = modifier
    )
}

private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int =
    android.graphics.Color.argb(
        (alpha * 255f).toInt().coerceIn(0, 255),
        (red * 255f).toInt().coerceIn(0, 255),
        (green * 255f).toInt().coerceIn(0, 255),
        (blue * 255f).toInt().coerceIn(0, 255)
    )

@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (language != null) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp
            )
        }
    }
}
