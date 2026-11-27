package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.PromptReferences
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraDraftReferences
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraMessageReference
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun AuroraReferenceEditor(
    references: AuroraDraftReferences,
    chatId: String,
    projectId: String?,
    touch: Boolean,
    maxHeight: Dp,
    canSend: Boolean,
    onSend: () -> Unit,
    onAction: (AiStudioAction) -> Unit,
    promptReferences: PromptReferences?,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val interaction = LocalAuroraReferenceInteraction.current
    val stored = remember(references) { AuroraQuoteEditorBuffer(references.nodes) }
    var buffer by remember(chatId, stored) { mutableStateOf(stored) }
    var field by remember(chatId) { mutableStateOf(TextFieldValue(stored.text, TextRange(stored.text.length))) }
    var quoteTops by remember(chatId) { mutableStateOf(emptyList<Int>()) }
    var preferredQuoteOffset by remember(chatId) { mutableStateOf<Int?>(null) }
    var verticalColumn by remember(chatId) { mutableStateOf<Float?>(null) }
    var focused by remember(chatId) { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val focus = interaction?.requestedFocus(references.nodes.size)
    if (field.text != buffer.text) {
        field = TextFieldValue(buffer.text, TextRange(field.selection.end.coerceIn(0, buffer.text.length)))
        preferredQuoteOffset = null
        verticalColumn = null
    }
    LaunchedEffect(stored, focus) {
        if (focus != null) {
            val offset = stored.offsetFor(focus)
            field = field.copy(selection = TextRange(offset), composition = null)
            preferredQuoteOffset = if (focus.afterQuote) offset - 1 else null
            verticalColumn = null
            interaction.focusHandled()
            requester.requestFocus()
            interaction.position = stored.cursorAt(offset)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(scroll)) {
        val density = LocalDensity.current
        val style = auroraMessageStyle()
        val measurer = rememberTextMeasurer()
        val caretGap = HbAuroraLayout.SmallGap.coerceAtMost(maxWidth / 8)
        val startInset = caretGap
        val endInset = 20.dp.coerceAtMost(maxWidth / 4)
        val marker = remember(maxWidth, startInset, style, density, measurer) {
            val spaceWidth = measurer.measure("\u00A0", style).multiParagraph.maxIntrinsicWidth.coerceAtLeast(1f)
            val width = with(density) { (maxWidth - startInset - HbAuroraLayout.SmallGap).toPx() }
            "\u00A0".repeat(floor(width / spaceWidth).toInt().coerceIn(1, 10_000))
        }
        val renderedField = field
        val renderedBuffer = buffer
        val chipStyle = promptLinkSpanStyle()
        val links = remember(renderedField.text, chipStyle) { PromptLinkTransformation(renderedField.text, chipStyle) }
        fun currentBand() = buffer.quoteIndexAt(field.selection.end, preferredQuoteOffset)
        val caretBand = currentBand()
        val placeCaret: (Int, Boolean) -> Unit = { offset, before ->
            verticalColumn = null
            preferredQuoteOffset = offset
            field = field.copy(selection = TextRange(offset + if (before) 0 else 1), composition = null)
            interaction?.position = buffer.cursorAt(field.selection.end)
            requester.requestFocus()
        }
        fun edit(edited: TextFieldValue) {
            val next = when {
                field.text.length > edited.text.length -> buffer.removeEmptyQuoteRow(edited)
                else -> preferredQuoteOffset?.let { buffer.insertAtQuoteEdge(field, edited, it) } ?: edited
            }
            verticalColumn = null
            if (field.text != next.text) {
                preferredQuoteOffset = null
                buffer = buffer.edit(field, next)
                if (buffer.quotes.isEmpty()) {
                    interaction?.requestFocus(
                        AuroraReferenceInteraction.Cursor(0, next.selection.end), afterPartCountChange = true,
                    )
                }
                onAction(AiStudioAction.MessageReference.EditContent(chatId, buffer.parts))
            } else if (field.selection != next.selection) {
                preferredQuoteOffset = null
            }
            field = next
            if (focused) interaction?.position = buffer.cursorAt(next.selection.end)
        }
        val caretHeight = remember(measurer, style, density) {
            with(density) { measurer.measure("M", style).getCursorRect(0).height.toDp() }
        }
        val rightEdge = maxWidth - endInset
        val quoteKeys = remember(renderedBuffer.quotes.map { it.reference }) {
            val seen = mutableMapOf<AuroraMessageReference, Int>()
            renderedBuffer.quotes.map {
                val occurrence = seen[it.reference] ?: 0
                seen[it.reference] = occurrence + 1
                it.reference to occurrence
            }
        }
        SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
            // Measure the cards first, so even a newly inserted or expanded card reserves its exact height this frame.
            val cards = renderedBuffer.quotes.mapIndexed { index, quote ->
                subcompose(quoteKeys[index]) {
                    Box(Modifier.fillMaxWidth()) {
                        AuroraQuoteReference(
                            quote.reference, touch, onOpen = { interaction?.jump(quote.reference) },
                            modifier = Modifier.offset(x = startInset).width(rightEdge - startInset),
                            selected = quote.offset in renderedField.selection.min until renderedField.selection.max,
                        )
                        Box(Modifier.matchParentSize()) {
                            AuroraQuoteCaretTarget(0.dp, startInset) { placeCaret(quote.offset, true) }
                            AuroraQuoteCaretTarget(rightEdge, endInset) { placeCaret(quote.offset, false) }
                            if (focused && renderedField.selection.collapsed && caretBand == index) {
                                val x = if (renderedField.selection.end == quote.offset) 0.dp else rightEdge + caretGap
                                AuroraQuoteCaret(Modifier.align(Alignment.BottomStart).offset(x = x), caretHeight)
                            }
                        }
                    }
                }.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            }
            val heights = cards.map { it.height }
            val input = subcompose(Unit) {
                val quoteHeights = renderedBuffer.quotes.indices.map { index ->
                    with(density) {
                        val next = renderedBuffer.quotes.getOrNull(index + 1)
                        val gap = if (next?.offset == renderedBuffer.quotes[index].offset + 1) HbAuroraLayout.SmallGap.roundToPx() else 0
                        (heights[index] + gap).toSp()
                    }
                }
                val quoteDisplay = remember(links, renderedBuffer, marker, quoteHeights) {
                    AuroraQuoteVisualTransformation(links, renderedBuffer.quotes, marker, quoteHeights::get)
                }
                AuroraComposerField(
                    field = renderedField,
                    onEdit = { edited ->
                        // On focus loss BasicTextField can collapse a selection in its last rendered
                        // value after the deletion has already updated the draft.
                        if (edited.text != renderedField.text || field.text == renderedField.text) edit(edited)
                    },
                    onSend = onSend,
                    touch = touch,
                    canSend = canSend,
                    maxTextHeight = maxHeight,
                    focused = focused,
                    referenceDraftId = chatId,
                    referenceProjectId = projectId,
                    references = promptReferences,
                    onAction = onAction,
                    onNavigateReferences = {},
                    showPlaceholder = false,
                    modifier = fieldModifier.fillMaxWidth().focusRequester(requester)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (focused) interaction?.position = buffer.cursorAt(field.selection.end)
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isShiftPressed) {
                                return@onPreviewKeyEvent false
                            }
                            if (event.key == Key.A && event.isCtrlPressed != event.isMetaPressed) {
                                edit(field.copy(selection = TextRange(0, field.text.length), composition = null))
                                return@onPreviewKeyEvent true
                            }
                            if (event.isCtrlPressed || event.isMetaPressed ||
                                (event.key != Key.Backspace && event.key != Key.Delete && event.key != Key.NumPadDelete)
                            ) return@onPreviewKeyEvent false

                            val selection = if (field.selection.collapsed) {
                                val offset = field.selection.end - if (event.key == Key.Backspace) 1 else 0
                                if (buffer.quotes.none { it.offset == offset }) return@onPreviewKeyEvent false
                                TextRange(offset, offset + 1)
                            } else field.selection
                            val text = field.annotatedString
                            edit(field.copy(
                                annotatedString = text.subSequence(0, selection.min) + text.subSequence(selection.max, text.length),
                                selection = TextRange(selection.min),
                                composition = null,
                            ))
                            true
                        },
                    quoteDisplay = quoteDisplay,
                    quoteCaretPosition = quoteDisplay.bands.getOrNull(caretBand)?.start?.takeIf { renderedField.selection.collapsed },
                    onQuoteTextPress = {
                        preferredQuoteOffset = null
                        verticalColumn = null
                    },
                    onQuoteHorizontalMove = move@{ direction ->
                        if (!field.selection.collapsed || field.composition != null) return@move false
                        val offset = field.selection.end
                        val step = when (direction) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            else -> return@move false
                        }
                        val adjacent = buffer.quotes.firstOrNull { it.offset == minOf(offset, offset + step) }
                        val current = buffer.quotes.getOrNull(currentBand())
                        when {
                            adjacent != null -> {
                                // Enter an adjoining row first. Only cross the object when already on its edge.
                                if (current == adjacent) {
                                    field = field.copy(selection = TextRange(offset + step))
                                    interaction?.position = buffer.cursorAt(field.selection.end)
                                }
                                preferredQuoteOffset = adjacent.offset
                            }
                            current != null && offset + step in 0..field.text.length -> {
                                preferredQuoteOffset = null
                                if (step > 0 && offset == current.offset + 1) {
                                    field = field.copy(selection = TextRange(offset + field.text.lineBreakLengthAt(offset)))
                                    interaction?.position = buffer.cursorAt(field.selection.end)
                                }
                            }
                            else -> return@move false
                        }
                        verticalColumn = null
                        true
                    },
                    onQuoteVerticalMove = move@{ layout, step, extendSelection ->
                        val band = currentBand()
                        val quote = buffer.quotes.getOrNull(band)
                        val offset = field.selection.end
                        val displayOffset = quoteDisplay.bands.getOrNull(band)?.start ?: quoteDisplay.displayOffset(offset)
                        val targetLine = layout.getLineForOffset(displayOffset) + step
                        val outside = targetLine !in 0 until layout.lineCount
                        if (outside && quote == null) return@move true
                        val targetBand = if (outside) band else quoteDisplay.bands.indexOfFirst {
                            layout.getLineForOffset(it.start) == targetLine
                        }
                        val targetQuote = buffer.quotes.getOrNull(targetBand)
                        val target = when {
                            targetQuote != null -> {
                                val after = when {
                                    outside -> step > 0
                                    quote == null -> step < 0
                                    else -> offset > quote.offset
                                }
                                targetQuote.offset + if (after) 1 else 0
                            }
                            quote != null -> if (step < 0) quote.offset else {
                                val after = quote.offset + 1
                                after + field.text.lineBreakLengthAt(after)
                            }
                            else -> {
                                val column = verticalColumn ?: layout.getCursorRect(displayOffset).left
                                verticalColumn = column
                                val y = (layout.getLineTop(targetLine) + layout.getLineBottom(targetLine)) / 2
                                val displayPosition = layout.getOffsetForPosition(Offset(column, y))
                                quoteDisplay.filter(field.annotatedString).offsetMapping.transformedToOriginal(displayPosition)
                            }
                        }
                        if (quote != null || targetQuote != null) verticalColumn = null
                        preferredQuoteOffset = targetQuote?.offset
                        field = field.copy(selection = TextRange(if (extendSelection) field.selection.start else target, target))
                        interaction?.position = buffer.cursorAt(target)
                        true
                    },
                    onQuoteCaret = { rect ->
                        val viewport = scroll.viewportSize
                        if (viewport > 0) {
                            val target = when {
                                rect.bottom > scroll.value + viewport -> (rect.bottom - viewport).roundToInt()
                                rect.top < scroll.value -> rect.top.roundToInt()
                                else -> scroll.value
                            }.coerceIn(0, scroll.maxValue)
                            if (target != scroll.value) scroll.scrollTo(target)
                        }
                    },
                    onQuoteLayout = { layout ->
                        quoteTops = quoteDisplay.bands.map { band ->
                            layout.getLineTop(layout.getLineForOffset(band.start)).roundToInt()
                        }
                    },
                )
            }.single().measure(constraints)
            val inset = composerFieldVerticalPadding(touch).roundToPx()
            layout(input.width, input.height) {
                input.placeRelative(0, 0)
                cards.forEachIndexed { index, card ->
                    quoteTops.getOrNull(index)?.let { top -> card.placeRelative(0, inset + top) }
                }
            }
        }
    }
}

@Composable
private fun AuroraQuoteCaretTarget(x: Dp, width: Dp, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    Box(Modifier.offset(x = x).width(width).fillMaxHeight().pointerInput(Unit) {
        detectTapGestures { click() }
    })
}

@Composable
private fun AuroraQuoteCaret(modifier: Modifier, height: Dp) {
    var visible by remember(modifier) { mutableStateOf(true) }
    LaunchedEffect(modifier) {
        while (true) {
            delay(500)
            visible = !visible
        }
    }
    if (visible) {
        Box(modifier.size(1.dp, height).background(HbAuroraColors.Primary))
    }
}
