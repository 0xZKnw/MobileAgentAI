package com.example.llamaapp.ui.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j

@Composable
fun MarkdownRenderer(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val textColorInt = textColor.toArgb()

    val markwon = remember(context) {
        val prism4j = buildPrism4j()
        val builder = Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))

        if (prism4j != null) {
            builder.usePlugin(
                SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create())
            )
        }

        builder.build()
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColorInt)
                textSize = 14f
                isClickable = true
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(textColorInt)
            markwon.setMarkdown(textView, text)
        },
        modifier = modifier
    )
}

/**
 * Attempts to instantiate Prism4j with the generated GrammarLocatorDef.
 * Falls back to a no-op locator if the annotation-processor artifact is absent.
 */
private fun buildPrism4j(): Prism4j? {
    return try {
        val locatorClass = Class.forName("io.noties.prism4j.GrammarLocatorDef")
        val locatorInstance = locatorClass
            .getDeclaredConstructor()
            .newInstance() as io.noties.prism4j.GrammarLocator
        Prism4j(locatorInstance)
    } catch (_: ClassNotFoundException) {
        // GrammarLocatorDef not generated — degrade gracefully, no syntax highlighting
        null
    } catch (_: Exception) {
        null
    }
}
