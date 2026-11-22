@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AuroraRichTable(table: PreparedTable, touch: Boolean) {
    val opener = remember { FocusRequester() }
    val inlineHorizontal = rememberScrollState()
    val inlineVertical = rememberLazyListState()
    val viewerHorizontal = rememberScrollState()
    val viewerVertical = rememberLazyListState()
    var expanded by remember { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    SideEffect(expanded) {
        if (!expanded && returnFocus) {
            opener.requestFocus()
            returnFocus = false
        }
    }
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    Column(
        Modifier.fillMaxWidth().clip(shape).border(AuroraLayout.Stroke, AuroraColors.MessageOutline, shape),
    ) {
        AuroraTableActions(table, touch, expanded = false, onToggle = { expanded = true }, toggleFocus = opener)
        AuroraTableContent(table, touch, inlineHorizontal, inlineVertical)
    }
    if (expanded) {
        val close = {
            returnFocus = true
            expanded = false
        }
        AuroraPanelDialog(close) {
            val closer = remember { FocusRequester() }
            SideEffect(closer) { closer.requestFocus() }
            SelectionContainer {
                Column(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        close()
                        true
                    } else {
                        false
                    }
                }) {
                    AuroraTableActions(table, touch, expanded = true, onToggle = close, toggleFocus = closer)
                    Box(Modifier.padding(if (touch) AuroraLayout.ContentInset else AuroraLayout.PaneGap)) {
                        AuroraTableContent(table, touch, viewerHorizontal, viewerVertical, expanded = true)
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("InjectDispatcher") // Build the clipboard text off the UI thread before writing it.
private fun AuroraTableActions(
    table: PreparedTable,
    touch: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    toggleFocus: FocusRequester,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(confirmation) {
        if (confirmation == 0) return@LaunchedEffect
        copied = true
        delay(HbMotions.Default.copyFeedbackMs.toLong())
        copied = false
    }
    DisableSelection {
        Row(
            Modifier.fillMaxWidth().background(AuroraColors.WorkHover)
                .padding(horizontal = AuroraLayout.ContentInset, vertical = AuroraLayout.SmallGap)
                .heightIn(min = if (touch || expanded) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
        ) {
            BasicText("Таблица", Modifier.weight(1f), auroraTimestampStyle())
            AuroraTableButton(
                label = if (copied) "Таблица скопирована" else "Копировать таблицу",
                touch = touch,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                onClick = {
                    scope.launch {
                        val text = withContext(Dispatchers.Default) { table.clipboardText() }
                        if (runCatching { clipboard.setText(AnnotatedString(text)) }.isSuccess) confirmation++
                    }
                },
            ) {
                HbIcon(if (copied) HbIconGlyph.Check else HbIconGlyph.Copy, size = AuroraLayout.Glyph)
            }
            AuroraTableButton(
                label = if (expanded) "Закрыть просмотр" else "Открыть на всё окно",
                touch = touch,
                modifier = Modifier.focusRequester(toggleFocus),
                onClick = onToggle,
            ) {
                if (expanded) HbIcon(HbIconGlyph.Close, size = AuroraLayout.Glyph) else AuroraExpandTableIcon()
            }
        }
    }
}

@Composable
private fun AuroraTableButton(
    label: String,
    touch: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    AuroraControl(
        onClick = onClick, modifier = modifier, touch = touch, label = label,
        background = Color.Transparent, horizontalPadding = Dp.Hairline, round = false,
    ) {
        val target = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget
        Box(Modifier.size(target), contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun AuroraExpandTableIcon() {
    Canvas(Modifier.size(AuroraLayout.Glyph)) {
        for (direction in listOf(-1, 1)) {
            val sign = direction.toFloat()
            val corner = center + Offset(size.width * .34f * sign, -size.height * .34f * sign)
            val centerOffset = AuroraLayout.FrameStroke.toPx() * sign
            drawLine(
                AuroraColors.MessageMuted, center + Offset(centerOffset, -centerOffset), corner,
                AuroraLayout.WorkEventStroke.toPx(), StrokeCap.Round,
            )
            drawLine(AuroraColors.MessageMuted, corner, corner - Offset(AuroraLayout.SmallGap.toPx() * sign, 0f),
                AuroraLayout.WorkEventStroke.toPx(), StrokeCap.Round)
            drawLine(AuroraColors.MessageMuted, corner, corner + Offset(0f, AuroraLayout.SmallGap.toPx() * sign),
                AuroraLayout.WorkEventStroke.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun AuroraTableContent(
    table: PreparedTable,
    touch: Boolean,
    horizontal: ScrollState,
    vertical: LazyListState,
    expanded: Boolean = false,
) {
    val rowStyle = auroraMessageStyle()
    val headerStyle = rowStyle.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    val overflow = vertical.canScrollForward || vertical.canScrollBackward
    val reserve = if (overflow) AuroraLayout.NavigationTrack else Dp.Hairline
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val naturalWidths = table.columnCharacters.map { characters ->
            (characters * rowStyle.fontSize.value * .55f + 24f).dp
                .coerceIn(AuroraLayout.TableColumnMin, AuroraLayout.TableColumnMax)
        }
        val extra = (maxWidth - reserve - naturalWidths.sumOf { it.value.toDouble() }.dp).coerceAtLeast(Dp.Hairline) /
            naturalWidths.size.coerceAtLeast(1)
        val widths = naturalWidths.map { it + extra }
        Column {
            Box(if (expanded) Modifier.weight(1f, fill = false) else Modifier) {
                Column(Modifier.fillMaxWidth().padding(end = reserve).horizontalScroll(horizontal)) {
                    Box(Modifier.onSizeChanged { headerHeight = it.height }) {
                        AuroraTableRow(table.header, table.alignments, widths, headerStyle, headingRow = true)
                    }
                    val body = if (expanded) {
                        Modifier.weight(1f, fill = false)
                    } else {
                        Modifier.heightIn(max = AuroraLayout.CodeViewportHeight)
                    }
                    LazyColumn(body, state = vertical) {
                        itemsIndexed(table.rows) { index, row ->
                            AuroraTableRow(
                                row, table.alignments, widths, rowStyle, headingRow = false, revealRow = index,
                            )
                        }
                    }
                }
                if (reserve != Dp.Hairline) {
                    Box(
                        Modifier.matchParentSize().padding(top = with(density) { headerHeight.toDp() }),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        AuroraHistoryScrollbar(
                            vertical, "Вертикальная прокрутка таблицы", reverse = false, touch = touch,
                        )
                    }
                }
            }
            AuroraLocalHorizontalScrollbar(horizontal, touch, Modifier.padding(end = reserve))
        }
    }
}

@Composable
private fun AuroraTableRow(
    cells: List<AnnotatedString>,
    alignments: List<ReplyAlignment>,
    widths: List<Dp>,
    style: TextStyle,
    headingRow: Boolean,
    revealRow: Int = -1,
) {
    val background = if (headingRow) Modifier.background(AuroraColors.WorkHistory) else Modifier
    Row(background.drawBehind {
        val y = size.height - AuroraLayout.Stroke.toPx() / 2
        drawLine(AuroraColors.MessageOutline, Offset(0f, y), Offset(size.width, y), AuroraLayout.Stroke.toPx())
    }) {
        widths.forEachIndexed { index, width ->
            val align = when (alignments.getOrNull(index)) {
                ReplyAlignment.Right -> TextAlign.Right
                ReplyAlignment.Center -> TextAlign.Center
                else -> TextAlign.Left
            }
            AuroraReplyText(
                cells.getOrNull(index) ?: AnnotatedString(""),
                Modifier.width(width).padding(horizontal = AuroraLayout.ContentInset, vertical = AuroraLayout.Space)
                    .then(if (headingRow) Modifier.semantics { heading() } else Modifier),
                style.copy(textAlign = align),
                revealKey = "table/$revealRow/$index",
            )
        }
    }
}
