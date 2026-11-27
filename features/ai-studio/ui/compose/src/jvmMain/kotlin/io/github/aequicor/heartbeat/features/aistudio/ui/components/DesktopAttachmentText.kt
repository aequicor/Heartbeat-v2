package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.previewCodeLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal sealed interface AttachmentPreviewDocument {

    val searchableRows: List<String>

    data object Unavailable : AttachmentPreviewDocument {

        override val searchableRows: List<String> = emptyList()
    }

    data class Source(val lines: List<AnnotatedString>) : AttachmentPreviewDocument {

        override val searchableRows: List<String> = lines.map { it.text }
    }

    data class Markdown(
        val blocks: List<PreparedBlock>,
        val plainLines: List<String>,
    ) : AttachmentPreviewDocument {

        override val searchableRows: List<String> = if (blocks.isEmpty()) plainLines else blocks.map(PreparedBlock::searchableText)
    }
}

@Composable
@Suppress("InjectDispatcher") // Formatting is scoped to the open document and its current display mode.
internal fun rememberAttachmentDocument(file: File, source: Boolean): AttachmentPreviewDocument? = key(file, source) {
    val font = auroraCodeFontFamily()
    val document by produceState<AttachmentPreviewDocument?>(null, file, source, font) {
        if (file.image != null) return@produceState
        value = try {
            withContext(Dispatchers.Default) {
                val text = file.previewText() ?: return@withContext AttachmentPreviewDocument.Unavailable
                val markdown = file.markdownContent
                if (!source && markdown != null) {
                    val parsed = markdown.document()
                    val plain = parsed.plainText
                    if (plain != null) AttachmentPreviewDocument.Markdown(emptyList(), plain.lines())
                    else AttachmentPreviewDocument.Markdown(parsed.prepareRichDocument(font).blocks, emptyList())
                } else {
                    val code = prepareAuroraCode(text, file.previewCodeLines(text), previous = null).lines
                    val isLog = file.name.endsWith(".log", ignoreCase = true)
                    AttachmentPreviewDocument.Source(if (isLog) code.map(::highlightLogLevel) else code)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AttachmentPreviewDocument.Unavailable
        }
    }
    document
}

@Composable
internal fun AttachmentTextViewport(
    content: AttachmentPreviewDocument?,
    supported: Boolean,
    wrap: Boolean,
    hit: AttachmentSearchHits.Hit?,
    queryLength: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        when (content) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AuroraText(if (supported) "Загрузка файла…" else "Предпросмотр недоступен")
            }
            AttachmentPreviewDocument.Unavailable -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AuroraText("Предпросмотр недоступен")
            }
            is AttachmentPreviewDocument.Source -> AttachmentSourceViewport(content.lines, wrap, hit, queryLength)
            is AttachmentPreviewDocument.Markdown -> AttachmentMarkdownViewport(content, hit, queryLength)
        }
    }
}

@Composable
private fun AttachmentSourceViewport(
    lines: List<AnnotatedString>,
    wrap: Boolean,
    hit: AttachmentSearchHits.Hit?,
    queryLength: Int,
) {
    val list = rememberLazyListState()
    val horizontal = rememberScrollState()
    val style = HbTypography.Code.copy(color = Colors.DesktopInk, fontSize = 14.sp, lineHeight = 20.sp)
    val numberStyle = style.copy(color = Colors.DesktopWorkMuted, fontSize = 13.sp)
    val widthMeasurer = rememberTextMeasurer()
    val longest = remember(lines) { lines.maxByOrNull { it.length } }
    val contentWidth = if (!wrap && longest != null) with(LocalDensity.current) {
        widthMeasurer.measure(longest, style, softWrap = false).size.width.toDp()
    } else 0.dp
    LaunchedEffect(hit) { hit?.let { list.scrollToItem(it.row) } }
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val width = maxOf(maxWidth, contentWidth + 84.dp)
        val scroller = if (wrap) Modifier else Modifier.horizontalScroll(horizontal)
        SelectionContainer(Modifier.fillMaxSize().then(scroller)) {
            Box(Modifier.width(width).fillMaxHeight()) {
                Box(Modifier.fillMaxHeight().width(LineNumberWidth).background(Colors.ViewerLineNumbers))
                LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp), state = list) {
                    items(lines.size) { index ->
                        val match = hit?.takeIf { it.row == index }
                        Row(Modifier.fillMaxWidth().heightIn(min = 20.dp)) {
                            DisableSelection {
                                BasicText(
                                    (index + 1).toString(), Modifier.width(LineNumberWidth).padding(start = 18.dp),
                                    style = numberStyle,
                                )
                            }
                            BasicText(
                                lines[index].showHit(match?.offset, queryLength),
                                Modifier.weight(1f).padding(start = 14.dp),
                                style = style, softWrap = wrap,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentMarkdownViewport(
    document: AttachmentPreviewDocument.Markdown,
    hit: AttachmentSearchHits.Hit?,
    queryLength: Int,
) {
    val list = rememberLazyListState()
    val style = HbTypography.Body.copy(color = Colors.DesktopInk)
    LaunchedEffect(hit) { hit?.let { list.scrollToItem(it.row) } }
    SelectionContainer(Modifier.widthIn(max = Layout.Composer).fillMaxWidth().fillMaxHeight().padding(24.dp)) {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            if (document.blocks.isEmpty()) {
                items(document.plainLines.size) { index ->
                    BasicText(
                        AnnotatedString(document.plainLines[index]).showHit(hit?.takeIf { it.row == index }?.offset, queryLength),
                        style = style,
                    )
                }
            } else {
                items(document.blocks.size) { index ->
                    val block = document.blocks[index]
                    if (hit?.row == index) {
                        Box(Modifier.fillMaxWidth().background(Colors.ActiveSearchMatch)) {
                            AuroraRichBlocks(listOf(block), touch = false, streaming = false, firstGroup = index == 0, bodyStyle = style)
                        }
                    } else {
                        AuroraRichBlocks(listOf(block), touch = false, streaming = false, firstGroup = index == 0, bodyStyle = style)
                    }
                }
            }
        }
    }
}

private fun PreparedBlock.searchableText(): String = buildString {
    append(text.text)
    for (group in children) group.forEach {
        append(' ')
        append(it.searchableText())
    }
    table?.let {
        append(' ')
        append(it.clipboardText())
    }
    code?.lines?.forEach {
        append(' ')
        append(it.text)
    }
}

private fun AnnotatedString.showHit(offset: Int?, length: Int): AnnotatedString {
    if (offset == null) return this
    return buildAnnotatedString {
        append(this@showHit)
        addStyle(SpanStyle(background = Colors.ActiveSearchMatch), offset, (offset + length).coerceAtMost(this@showHit.length))
    }
}

private fun highlightLogLevel(line: AnnotatedString): AnnotatedString {
    val level = LogLevel.find(line.text) ?: return line
    val ink = when (level.value.uppercase()) {
        "ERROR", "FATAL", "CRITICAL" -> Colors.Error
        "WARN", "WARNING" -> Colors.SyntaxNumber
        "INFO" -> Colors.SyntaxKeyword
        else -> Colors.SyntaxComment
    }
    return buildAnnotatedString {
        append(line)
        addStyle(SpanStyle(color = ink), level.range.first, level.range.last + 1)
    }
}

@Composable
@Suppress("InjectDispatcher") // Search stops when the query or preview changes.
internal fun rememberAttachmentHits(content: AttachmentPreviewDocument?, query: String): AttachmentSearchHits = key(content, query) {
    val hits by produceState(AttachmentSearchHits.Empty, content, query) {
        value = if (content == null || query.isEmpty()) AttachmentSearchHits.Empty else withContext(Dispatchers.Default) {
            AttachmentSearchHits.search(content.searchableRows, query)
        }
    }
    hits
}

private val LineNumberWidth = 56.dp
private val LogLevel = Regex("""\b(?:TRACE|DEBUG|INFO|WARN(?:ING)?|ERROR|FATAL|CRITICAL)\b""", RegexOption.IGNORE_CASE)
