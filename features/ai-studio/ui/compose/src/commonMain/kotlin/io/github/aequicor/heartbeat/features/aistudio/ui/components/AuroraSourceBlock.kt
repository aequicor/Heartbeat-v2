@file:Suppress("MagicNumber", "DEPRECATION")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.verticalScroll
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyDiagramKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class BlockView(source: Boolean = false, wrapped: Boolean = false, full: Boolean = false, scale: Float = 1f) {
    var sourceMode by mutableStateOf(source)
    var wrap by mutableStateOf(wrapped)
    var expanded by mutableStateOf(full)
    var zoom by mutableFloatStateOf(scale)
    var retry by mutableIntStateOf(0)
    var copied by mutableIntStateOf(0)
    var restoreFocus by mutableStateOf(false)
    val opener = FocusRequester()
    val returningToOpener get() = !expanded && restoreFocus

    fun close() {
        restoreFocus = true
        expanded = false
    }

    fun scaleBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(.25f, 4f)
    }

    companion object {
        val Saver = listSaver<BlockView, Any>(
            save = { listOf(it.sourceMode, it.wrap, it.expanded, it.zoom) },
            restore = { BlockView(it[0] as Boolean, it[1] as Boolean, it[2] as Boolean, it[3] as Float) },
        )
    }
}

private class BlockSource(val code: ReplyBlock.Code?, val diagram: ReplyBlock.Diagram?) {
    val text = code?.source ?: requireNotNull(diagram).source
    val hasContent = if (diagram == null) text.isNotEmpty() else text.isNotBlank()
    val format = when (diagram?.kind) {
        ReplyDiagramKind.Mermaid -> "Mermaid"
        ReplyDiagramKind.PlantUml -> "PlantUML"
        null -> code?.language ?: "Код"
    }
}

private class Preview(val bitmap: ImageBitmap? = null, val failed: Boolean = false) {
    fun unavailable(source: BlockSource, streaming: Boolean) = failed || (!streaming && source.diagram?.closed == false)
}

private val NoPreview = Preview()

@Composable
private fun diagramPreview(source: BlockSource, view: BlockView): Preview {
    val diagram = source.diagram ?: return NoPreview
    val cache = LocalDiagramPreviews.current ?: remember { AuroraDiagramPreviews() }
    val complete = diagram.takeIf { it.closed && source.hasContent }?.source
    var result by remember(complete, source.format) { mutableStateOf(Preview()) }
    LaunchedEffect(complete, source.format, view.retry) {
        if (complete == null) return@LaunchedEffect
        if (result.failed) result = Preview()
        result = try {
            Preview(cache.preview(source.format, complete))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Preview(failed = true)
        }
    }
    return result
}

@Composable
@Suppress("InjectDispatcher") // The visible source block owns this background preparation.
private fun preparedSource(source: BlockSource, show: Boolean): AuroraPreparedCode? {
    var prepared by remember { mutableStateOf<AuroraPreparedCode?>(null) }
    LaunchedEffect(if (show) source.text else null, show) {
        if (show) {
            val previous = prepared
            prepared = withContext(Dispatchers.Default) {
                prepareAuroraCode(source.text, source.code?.lines, previous)
            }
        }
    }
    return prepared
}

@Composable
internal fun AuroraSourceBlock(
    block: ReplyBlock, touch: Boolean, streaming: Boolean, preparedCode: AuroraPreparedCode? = null,
) {
    val source = BlockSource(block as? ReplyBlock.Code, block as? ReplyBlock.Diagram)
    val view = rememberSaveable(saver = BlockView.Saver) { BlockView() }
    val preview = diagramPreview(source, view)
    val prepared = preparedCode ?: preparedSource(source, source.code != null || view.sourceMode)
    val sourceX = rememberScrollState()
    val sourceY = rememberLazyListState()
    val openedStreaming = rememberSaveable { streaming }
    val imageScroll = if (source.diagram != null) rememberScrollState() to rememberScrollState() else null
    var inlineHeight by remember { mutableIntStateOf(0) }
    val body: @Composable () -> Unit = {
        SourceBlockLayout(source, view, preview, touch, { inlineHeight = it }) {
            when {
                view.sourceMode || source.code != null -> prepared?.let {
                    AuroraCodeViewport(it, view.wrap, touch, view.expanded, sourceX, sourceY, openedStreaming)
                }
                !source.hasContent -> AuroraText("Нет данных для диаграммы", small = true)
                preview.unavailable(source, streaming) ->
                    AuroraText("Не удалось отобразить диаграмму", small = true, color = Colors.Error)
                preview.bitmap != null -> AuroraDiagramImage(
                    preview.bitmap, source.format, view.expanded, view.zoom,
                    requireNotNull(imageScroll).first, imageScroll.second, touch, view::scaleBy,
                )
                else -> AuroraText("Подготавливаю диаграмму…", small = true)
            }
        }
    }
    if (view.returningToOpener) SideEffect(view.opener) {
        view.opener.requestFocus()
        view.restoreFocus = false
    }
    if (view.expanded) {
        Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { inlineHeight.toDp() }))
        AuroraPanelDialog(view::close) { SelectionContainer { body() } }
    } else body()
}

@Composable
private fun SourceBlockLayout(
    source: BlockSource, view: BlockView, preview: Preview, touch: Boolean,
    onInlineHeight: (Int) -> Unit, content: @Composable () -> Unit,
) {
    val modal = view.expanded
    val surface = if (modal) {
        Modifier.fillMaxSize().background(Colors.ViewerSurface).semantics { paneTitle = source.format }
            .onPreviewKeyEvent {
                val escape = it.key == Key.Escape && it.type == KeyEventType.KeyDown
                if (escape) view.close()
                escape
            }
    } else {
        Modifier.fillMaxWidth().onSizeChanged { onInlineHeight(it.height) }
            .clip(RoundedCornerShape(AuroraLayout.SmallRadius)).background(Colors.WorkHover)
    }
    Column(surface) {
        DisableSelection { SourceToolbar(source, view, preview, touch) }
        if (modal && !view.sourceMode && preview.bitmap != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SourceAction(
                    AuroraSourceAction.Smaller, touch, { view.scaleBy(1f / 1.25f) }, enabled = view.zoom > .25f,
                )
                SourceAction(AuroraSourceAction.Larger, touch, { view.scaleBy(1.25f) }, enabled = view.zoom < 4f)
            }
        }
        Box(Modifier.fillMaxWidth().padding(
            start = AuroraLayout.ContentInset, end = AuroraLayout.ContentInset,
            top = AuroraLayout.Space, bottom = AuroraLayout.ContentInset,
        )) { content() }
    }
}

@Composable
private fun SourceToolbar(source: BlockSource, view: BlockView, preview: Preview, touch: Boolean) {
    val edge = if (view.expanded) remember { FocusRequester() } else view.opener
    if (view.expanded) SideEffect(edge) { edge.requestFocus() }
    val height = if (view.expanded || touch) AuroraLayout.FileViewerHeader else AuroraLayout.FilePaneHeader
    val inset = if (view.expanded) AuroraLayout.PaneGap else AuroraLayout.ContentInset
    Row(
        Modifier.fillMaxWidth().background(Colors.WorkHover).heightIn(min = height)
            .padding(horizontal = inset, vertical = AuroraLayout.SmallGap),
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(source.format, Modifier.weight(1f), style = auroraTimestampStyle().copy(color = Colors.MessageMuted),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        SourceControls(source, view, preview, touch)
        SourceAction(
            if (view.expanded) AuroraSourceAction.Close else AuroraSourceAction.Expand, touch,
            onClick = {
                if (view.expanded) view.close() else {
                    if (source.diagram != null && preview.bitmap == null) view.sourceMode = true
                    view.expanded = true
                }
            },
            modifier = Modifier.focusRequester(edge), enabled = view.expanded || source.hasContent,
        )
    }
}

@Composable
private fun SourceControls(source: BlockSource, view: BlockView, preview: Preview, touch: Boolean) {
    val clipboard = LocalClipboardManager.current
    val enabled = source.hasContent
    if (view.copied > 0) LaunchedEffect(view.copied) {
        delay(HbMotions.Default.copyFeedbackMs.toLong())
        view.copied = 0
    }
    if (source.diagram != null) SourceAction(
        if (view.sourceMode) AuroraSourceAction.Preview else AuroraSourceAction.Source,
        touch, { view.sourceMode = !view.sourceMode }, enabled = enabled,
    )
    if (view.sourceMode || source.code != null) SourceAction(
        AuroraSourceAction.Wrap, touch, { view.wrap = !view.wrap }, enabled = enabled, selected = view.wrap,
    )
    SourceAction(if (view.copied > 0) AuroraSourceAction.Copied else AuroraSourceAction.Copy, touch, onClick = {
        if (runCatching { clipboard.setText(AnnotatedString(source.text)) }.isSuccess) view.copied++
    }, enabled = enabled)
    if (preview.failed) SourceAction(AuroraSourceAction.Retry, touch, { view.retry++ })
}

@Composable
private fun AuroraCodeViewport(
    code: AuroraPreparedCode, wrap: Boolean, touch: Boolean, modal: Boolean,
    horizontal: ScrollState, vertical: LazyListState, streaming: Boolean,
) {
    // Keep a growing or long source individually addressable by its logical line index.
    val compact = if (streaming) null else code.compact
    val rows = compact ?: code.lines
    val textStyle = HbTypography.Code.copy(color = Colors.MessageInk)
    val measurer = rememberTextMeasurer()
    val characterWidth = remember(textStyle, measurer) { measurer.measure("0", textStyle).size.width }
    val naturalWidth = with(LocalDensity.current) {
        (code.longestLine.toLong() * characterWidth).coerceAtMost(60_000L).toInt().toDp()
    }
    Column(Modifier.fillMaxWidth()) {
        Box(if (modal) Modifier.weight(1f, fill = false) else Modifier) {
            val canScroll = vertical.canScrollForward || vertical.canScrollBackward
            val scroll = if (wrap) Modifier else Modifier.horizontalScroll(horizontal)
            val height = if (modal) Modifier.fillMaxHeight() else
                Modifier.heightIn(max = AuroraLayout.CodeViewportHeight)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().then(height)
                    .padding(end = if (canScroll) AuroraLayout.ContentInset else Dp.Hairline)
                    .then(scroll).then(if (wrap) Modifier else Modifier.widthIn(min = naturalWidth)), state = vertical,
            ) {
                items(rows.size) { index ->
                    BasicText(rows[index], style = textStyle, softWrap = wrap)
                }
            }
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                AuroraHistoryScrollbar(
                    vertical, "Прокрутка кода", Modifier.fillMaxHeight(), reverse = false, touch = touch,
                    lineStepPx = with(LocalDensity.current) { textStyle.lineHeight.toPx() }.takeIf { compact != null },
                )
            }
        }
        if (!wrap) AuroraLocalHorizontalScrollbar(horizontal, touch)
    }
}

@Composable
private fun AuroraDiagramImage(
    bitmap: ImageBitmap, label: String, modal: Boolean, zoom: Float,
    horizontal: ScrollState, vertical: ScrollState, touch: Boolean, onScale: (Float) -> Unit,
) {
    val scale = if (modal) zoom else 1f
    val width = (bitmap.width * .5f * scale).dp
    val height = (bitmap.height * .5f * scale).dp
    val transform = rememberTransformableState { factor, pan, _ ->
        onScale(factor)
        horizontal.dispatchRawDelta(-pan.x)
        vertical.dispatchRawDelta(-pan.y)
    }
    Column {
        val viewport = if (modal) Modifier.weight(1f, fill = false) else {
            Modifier.heightIn(max = AuroraLayout.DiagramViewportHeight)
        }
        Box(
            Modifier.fillMaxWidth().then(viewport)
                .then(if (modal) Modifier.transformable(transform, canPan = { false }) else Modifier)
                .horizontalScroll(horizontal).verticalScroll(vertical),
        ) {
            Image(bitmap, label, Modifier.size(width, height), contentScale = ContentScale.FillBounds)
        }
        AuroraLocalHorizontalScrollbar(horizontal, touch)
    }
}
