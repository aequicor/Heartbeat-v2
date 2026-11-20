package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

internal actual fun supportsAuroraStagedRichLayout(): Boolean = true

private const val StartChunkingAt = 8_192
private const val ReshapeAfter = 4_096
private const val RetainedTail = 1_024
private const val MaxChunkLength = 16_384
private const val HardBreakAnnotation = "heartbeat.paragraph-break"

@Composable
internal actual fun AuroraStreamingPlainText(
    text: String,
    selection: SelectionState,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier,
) {
    val style = auroraMessageStyle()
    val source = remember { StreamingParagraphSource() }
    val direction = LocalLayoutDirection.current
    val eligible = direction == LayoutDirection.Ltr && text.length >= StartChunkingAt && source.canReuseWraps(text)
    if (eligible) {
        BoxWithConstraints(modifier) {
            val density = LocalDensity.current
            val resolver = LocalFontFamilyResolver.current
            key(source.generation, constraints.maxWidth, style, density, resolver, direction) {
                ChunkedParagraph(text, style, selection)
            }
        }
    } else {
        SelectionContainer(state = selection, modifier = modifier) {
            BasicText(text, style = style, onTextLayout = onTextLayout)
        }
    }
}

@Composable
private fun ChunkedParagraph(text: String, style: TextStyle, selection: SelectionState) {
    val state = remember { mutableStateOf(ParagraphPrefix()) }
    val prefix = keepSelectedReplyRevision(state.value, selection)
    val clipboard = LocalClipboard.current
    val exactClipboard = remember(selection, clipboard) { ParagraphClipboard(clipboard, selection) }
    CompositionLocalProvider(LocalClipboard provides exactClipboard) {
        SelectionContainer(state = selection) {
            Column {
                for (chunk in prefix.chunks) BasicText(chunk, Modifier.fillMaxWidth(), style = style)
                BasicText(text.substring(prefix.end), Modifier.fillMaxWidth(), style = style, onTextLayout = { layout ->
                    val measured = prefix.withMeasuredLines(layout)
                    if (measured != null) state.value = measured
                })
            }
        }
    }
}

private class StreamingParagraphSource {
    private var inspected = ""
    var generation = 0
        private set
    private var safe = true

    fun canReuseWraps(text: String): Boolean {
        if (text === inspected) return safe
        val append = text.startsWith(inspected)
        if (!append) {
            generation++
            safe = true
        }
        if (safe) {
            val start = if (append) inspected.length else 0
            safe = (start until text.length).all { index -> text[index].supportsWrapReuse() }
        }
        inspected = text
        return safe
    }
}

private class ParagraphPrefix(val end: Int = 0, val chunks: List<AnnotatedString> = listOf()) {
    fun withMeasuredLines(layout: TextLayoutResult): ParagraphPrefix? {
        val tail = layout.layoutInput.text.text
        var consumed = 0
        val measured = buildList {
            while (tail.length - consumed >= ReshapeAfter) {
                val cut = layout.prefixBoundary(tail, consumed) ?: break
                add(buildAnnotatedString {
                    append(tail, consumed, cut.displayEnd)
                    if (cut.displayEnd < cut.nextStart) addStringAnnotation(HardBreakAnnotation, "", 0, length)
                })
                consumed = cut.nextStart
            }
        }
        return if (measured.isEmpty()) null else ParagraphPrefix(end + consumed, chunks + measured)
    }
}

private data class ParagraphCut(val displayEnd: Int, val nextStart: Int)

// These scripts have no RTL runs. Other scripts still use native paragraph layout as a whole.
private fun Char.supportsWrapReuse(): Boolean =
    this in ' '..'~' || this in '\u0400'..'\u052F' || this in "\n—–«»„“”…"

private fun TextLayoutResult.prefixBoundary(tail: String, start: Int): ParagraphCut? {
    val lastSafeOffset = minOf(start + MaxChunkLength, tail.length - RetainedTail)
    // The live edge stays in the last text node, where Compose reflows new words normally.
    val candidate = getLineForOffset(lastSafeOffset) - 1
    return (candidate downTo getLineForOffset(start)).firstNotNullOfOrNull { line ->
        val end = getLineEnd(line)
        when {
            end > start + 1 && tail.getOrNull(end - 1) == '\n' -> ParagraphCut(end - 1, end)
            end > start && tail.getOrNull(end - 1) == ' ' && tail.getOrNull(end) != ' ' -> ParagraphCut(end, end)
            else -> null
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private class ParagraphClipboard(private val delegate: Clipboard, private val selection: SelectionState) :
    Clipboard by delegate {
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val parts = selection.selectedTexts
        val current = if (parts.size > 1) clipEntry?.plainText() else null
        val exact = if (current != null && current == parts.joinToString("\n") { it.text }) {
            ClipEntry(StringSelection(parts.exactText()))
        } else clipEntry
        delegate.setClipEntry(exact)
    }

    private fun List<AnnotatedString>.exactText(): String = buildString {
        this@exactText.forEachIndexed { index, part ->
            append(part.text)
            if (index < lastIndex && part.getStringAnnotations(HardBreakAnnotation, 0, part.length).isNotEmpty()) {
                append('\n')
            }
        }
    }

    private fun ClipEntry.plainText(): String? = try {
        asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as? String
    } catch (_: UnsupportedFlavorException) {
        null
    } catch (_: IOException) {
        null
    }
}
