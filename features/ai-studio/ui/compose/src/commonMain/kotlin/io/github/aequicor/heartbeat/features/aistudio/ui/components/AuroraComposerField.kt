package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.PromptReferences
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.prompt_reference_link
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraComposerField(
    field: TextFieldValue,
    onEdit: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    touch: Boolean,
    canSend: Boolean,
    maxTextHeight: Dp,
    focused: Boolean,
    referenceDraftId: String,
    referenceProjectId: String?,
    references: PromptReferences?,
    onAction: (AiStudioAction) -> Unit,
    onNavigateReferences: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val style = auroraMessageStyle()
    val focusManager = LocalFocusManager.current
    val onActionLatest by rememberUpdatedState(onAction)
    var dismissed by remember(referenceDraftId, field.text) { mutableStateOf<PromptReferenceQuery?>(null) }
    val query = field.promptReferenceQuery()?.takeIf { focused && !readOnly && it != dismissed }
    val results = references?.takeIf {
        query != null && it.draftId == referenceDraftId && it.projectId == referenceProjectId && it.query == query.query
    }
    val choices = results?.entries.orEmpty()
    var highlightedEntry by remember(query) { mutableStateOf<PromptReferences.Entry?>(null) }
    val highlighted = highlightedEntry?.takeIf { it in choices } ?: choices.firstOrNull()
    var requestedQuery by remember(referenceDraftId, referenceProjectId) { mutableStateOf<String?>(null) }
    LaunchedEffect(referenceDraftId, referenceProjectId, query?.query) {
        if (requestedQuery != query?.query) {
            requestedQuery = query?.query
            onActionLatest(AiStudioAction.SearchPromptReferences(referenceDraftId, referenceProjectId, query?.query))
        }
    }
    DisposableEffect(referenceDraftId, referenceProjectId) {
        onDispose {
            if (requestedQuery != null) {
                onActionLatest(AiStudioAction.SearchPromptReferences(referenceDraftId, referenceProjectId, null))
            }
        }
    }
    val chipStyle = promptLinkSpanStyle()
    val transformation = remember(field.text, chipStyle) { PromptLinkTransformation(field.text, chipStyle) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableStateOf(0) }
    var lastScroll by remember(referenceDraftId) { mutableFloatStateOf(0f) }
    fun currentTextLayout() = textLayout?.takeIf { it.layoutInput.text.text == transformation.displayText }
    val caretOffset = transformation.displayOffset(field.selection.end)
    val layout = currentTextLayout()
    val caret = layout?.getCursorRect(caretOffset)
    val scroll = if (caret == null) lastScroll else layout.scrollForCaret(caret, viewportHeight, lastScroll)
    SideEffect { if (scroll != lastScroll) lastScroll = scroll }
    fun select(reference: PromptReferences.Entry) {
        query?.let { onEdit(it.replace(field, reference)) }
    }
    BasicTextField(
        value = field,
        onValueChange = onEdit,
        readOnly = readOnly,
        modifier = modifier.heightIn(max = if (touch) AuroraLayout.ComposerMaxText else maxTextHeight)
            .padding(vertical = if (touch) AuroraLayout.ControlGap else AuroraLayout.ControlGap - AuroraLayout.Stroke)
            .semantics { contentDescription = "Сообщение" }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val plain = !event.isShiftPressed && !event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed
                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (query != null && plain) {
                    if (event.key == Key.Escape) {
                        dismissed = query
                        return@onPreviewKeyEvent true
                    }
                    if (choices.isNotEmpty()) {
                        val step = when (event.key) {
                            Key.DirectionUp -> -1
                            Key.DirectionDown -> 1
                            else -> 0
                        }
                        if (step != 0) {
                            val index = choices.indexOfFirst { it == highlighted }
                            highlightedEntry = choices[(index + step).mod(choices.size)]
                            onNavigateReferences()
                            return@onPreviewKeyEvent true
                        }
                        if (enter) {
                            select(highlighted ?: choices.first())
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                if (plain && !readOnly) transformation.editAtBoundary(field, event.key)?.let {
                    onEdit(it)
                    return@onPreviewKeyEvent true
                }
                if (touch) return@onPreviewKeyEvent false
                if (event.key == Key.Tab) {
                    // The multiline editor otherwise treats Tab as text input.
                    focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                    return@onPreviewKeyEvent true
                }
                val handled = enter && !event.isShiftPressed && field.composition == null
                if (handled && canSend) onSend()
                handled
            },
        textStyle = style,
        cursorBrush = SolidColor(AuroraColors.Primary),
        visualTransformation = transformation,
        onTextLayout = { textLayout = it },
        decorationBox = { content ->
            val linkRanges = transformation.displayLinks
            val decoration = if (linkRanges.isNotEmpty()) {
                Modifier.promptLinkDecoration(
                    layoutProvider = ::currentTextLayout,
                    links = linkRanges,
                    icon = painterResource(Res.drawable.prompt_reference_link),
                    scrollProvider = { latest -> latest.scrollForCaret(latest.getCursorRect(caretOffset), viewportHeight, lastScroll) },
                )
            } else Modifier
            Box(Modifier.onSizeChanged { viewportHeight = it.height }.then(decoration)) {
                if (field.text.isEmpty()) {
                    BasicText(
                        if (touch) "Сообщение…" else "Сообщение",
                        style = style.copy(color = if (touch) AuroraColors.MessageMuted else AuroraColors.ComposerPlaceholder),
                    )
                }
                content()
                if (query != null && caret != null && results != null) {
                    AuroraPromptReferencePopup(
                        caret.translate(0f, -scroll), choices, highlighted, { highlightedEntry = it }, ::select,
                        onDismiss = { dismissed = query },
                    )
                }
            }
        },
    )
}

private fun TextLayoutResult.scrollForCaret(caret: Rect, viewportHeight: Int, previousScroll: Float): Float {
    if (viewportHeight == 0) return previousScroll
    val offset = when {
        caret.bottom > previousScroll + viewportHeight -> caret.bottom - viewportHeight
        caret.top < previousScroll -> caret.top
        else -> previousScroll
    }
    return offset.coerceIn(0f, (size.height - viewportHeight).coerceAtLeast(0).toFloat())
}
