package com.salizzai.app.ui.components

import android.text.util.Linkify
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Compose wrapper for Markwon markdown renderer.
 * Renders chat responses with proper formatting.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Read colors OUTSIDE the AndroidView factory (factory is not @Composable)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .build()
    }

    AndroidView(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(android.graphics.Color.rgb(
                    (onSurfaceColor.red * 255).toInt(),
                    (onSurfaceColor.green * 255).toInt(),
                    (onSurfaceColor.blue * 255).toInt()
                ))
                textSize = 14f
                setLineSpacing(2f, 1.2f)
                Linkify.addLinks(this, Linkify.WEB_URLS)
                markwon.setMarkdown(this, markdown)
            }
        },
        update = { tv ->
            // Re-apply color in case theme changed (factory only runs once)
            tv.setTextColor(android.graphics.Color.rgb(
                (onSurfaceColor.red * 255).toInt(),
                (onSurfaceColor.green * 255).toInt(),
                (onSurfaceColor.blue * 255).toInt()
            ))
            markwon.setMarkdown(tv, markdown)
        },
        modifier = modifier
    )
}

@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
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
                fontSize = 13.sp
            )
        }
    }
}
