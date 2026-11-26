@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.chat_attachment_error
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_creation_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_project_unavailable
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraLowerControls(
    state: AiStudioScreenState,
    draft: StateFlow<Draft>,
    list: LazyListState,
    showTail: Boolean,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    maxPanelHeight: Dp,
    modifier: Modifier = Modifier,
    inlineRequestStop: Boolean = true,
    onPanelHeight: (Int) -> Unit = {},
    onTail: () -> Unit = { list.requestScrollToItem(0) },
) {
    val chat = state.chat ?: return
    val chatId = chat.id
    val text = draft.value.takeIf { it.chatId == chatId }?.text.orEmpty()
    var field by remember(chatId, draft) { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    LaunchedEffect(chatId, draft) {
        draft.collect { snapshot ->
            if (snapshot.chatId == chatId && snapshot == draft.value && snapshot.text != field.text) {
                field = TextFieldValue(snapshot.text, TextRange(snapshot.text.length))
            }
        }
    }
    val currentField = if (field.text == text) field else TextFieldValue(text, TextRange(text.length))
    val submitting = chatId in state.submittingDrafts
    val editing by rememberUpdatedState(state.request == null)
    val measuredPanel = Modifier.onSizeChanged { onPanelHeight(it.height) }
    Column(
        modifier.widthIn(max = AuroraLayout.Composer).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.ControlGap),
    ) {
        if (showTail) {
            AuroraTailControl(list, touch, onJump = onTail)
        }
        val request = state.request
        if (request != null) {
            AuroraRequestPanel(
                request, touch, onAction, measuredPanel.heightIn(max = maxPanelHeight), inlineStop = inlineRequestStop,
            )
        } else {
            AuroraComposer(
                field = currentField,
                submitting = submitting,
                onEdit = {
                    val current = draft.value
                    if (current.chatId == chatId) {
                        // Echo edits before the next IME event, even if its draft snapshot has not been collected yet.
                        if (editing || it.text != current.text) field = it
                        if (it.text != current.text) onAction(AiStudioAction.EditDraft(chatId, it.text))
                    }
                },
                activity = state.activity,
                sendBlocked = state.isPromptBlockedBySecurityMode(chat),
                files = state.attachments,
                touch = touch,
                attachmentDraftId = chatId.takeIf { state.allowsFileImport && !submitting },
                importingAttachment = chatId in state.importingAttachmentsFor,
                attachmentError = state.attachmentErrorFor == chatId,
                attach = {
                    if (state.allowsFileImport) {
                        AuroraAttachmentPickerButton(
                            chatId, chatId in state.importingAttachmentsFor, touch, onAction, enabled = !submitting,
                        )
                    } else {
                        AuroraAttachMenu(state.fileChoices, state.attachments, touch, onAction)
                    }
                },
                maxTextHeight = (maxPanelHeight / 2).coerceAtLeast(AuroraLayout.DesktopTarget),
                onAction = onAction,
                modifier = measuredPanel,
            )
        }
    }
}

@Composable
internal fun AuroraTailControl(
    list: LazyListState, touch: Boolean, modifier: Modifier = Modifier,
    onJump: () -> Unit = { list.requestScrollToItem(0) },
) {
    AuroraControl(onJump,
        label = "К новым сообщениям", touch = touch, modifier = modifier) {
        HbIcon(HbIconGlyph.ChevronDown, tint = AuroraColors.Muted, size = AuroraLayout.Glyph)
    }
}

@Composable
internal fun AuroraBootstrapComposer(
    state: AiStudioScreenState,
    bootstrap: SessionBootstrapState,
    chat: Chat,
    touch: Boolean,
    maxTextHeight: Dp,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember(bootstrap.id) { mutableStateOf(TextFieldValue("")) }
    val submitting = bootstrap.operation == SessionBootstrapState.Operation.CreatingSession
    val error = when (bootstrap.error) {
        SessionBootstrapState.Error.ProjectUnavailable -> Res.string.session_project_unavailable
        SessionBootstrapState.Error.StorageFailure -> if (bootstrap.pendingProject == null) {
            Res.string.session_creation_failed
        } else null
        else -> null
    }
    Column(
        modifier.widthIn(max = AuroraLayout.Composer).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
    ) {
        error?.let { AuroraHeaderStorageError(stringResource(it)) }
        AuroraComposer(
            field, { field = it }, Activity.Idle, bootstrap.attachments, touch, maxTextHeight,
            submitting = submitting,
            onAction = { action ->
                if (action == AiStudioAction.Send) {
                    onAction(AiStudioAction.SubmitSessionBootstrap(
                        bootstrap.project, field.text, chat.scenario, chat.access, chat.effort,
                    ))
                } else {
                    onAction(action)
                }
            },
            sendBlocked = bootstrap.operation != null || bootstrap.picker.confirmed == null || bootstrap.picker.step != null ||
                bootstrap.picker.securityModeBusy || bootstrap.picker.securityModeError != null || bootstrap.picker.securityMode?.requiresUserChoice == true,
            attachmentDraftId = bootstrap.id.takeIf { state.allowsFileImport && bootstrap.operation == null },
            importingAttachment = bootstrap.id in state.importingAttachmentsFor,
            attachmentError = state.attachmentErrorFor == bootstrap.id,
            attach = {
                if (state.allowsFileImport) {
                    AuroraAttachmentPickerButton(
                        bootstrap.id, bootstrap.id in state.importingAttachmentsFor, touch, onAction,
                        enabled = bootstrap.operation == null,
                    )
                }
            },
        )
    }
}

@Composable
private fun AuroraComposer(
    field: TextFieldValue,
    onEdit: (TextFieldValue) -> Unit,
    activity: Activity,
    files: List<File>,
    touch: Boolean,
    maxTextHeight: Dp,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
    sendBlocked: Boolean = false,
    submitting: Boolean = false,
    attachmentDraftId: String?,
    importingAttachment: Boolean = false,
    attachmentError: Boolean = false,
    attach: @Composable () -> Unit,
) {
    val working = activity != Activity.Idle
    val stopping = activity == Activity.Stopping
    val sendEnabled = !submitting && !sendBlocked && !importingAttachment && (field.text.isNotBlank() || files.isNotEmpty())
    val visibleField = if (submitting) TextFieldValue() else field
    val onSend = { onAction(AiStudioAction.Send) }
    val attachmentInput = rememberAttachmentInput(attachmentDraftId, importingAttachment, onAction)
    var focused by remember { mutableStateOf(false) }
    val surface = if (touch) {
        Modifier.background(AuroraColors.Pearl, RoundedCornerShape(AuroraLayout.ComposerRadius))
            .padding(horizontal = AuroraLayout.ControlGap, vertical = AuroraLayout.SmallGap)
    } else {
        Modifier.auroraComposerSurface(focused && HbFocusIndication.keyboardNavigation)
            .padding(AuroraLayout.ControlGap)
    }

    Column(
        modifier.fillMaxWidth().then(attachmentInput.dropModifier).then(surface),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
    ) {
        if (!submitting && files.isNotEmpty()) AuroraDraftAttachments(files, touch, onAction)
        if (attachmentError) AuroraHeaderStorageError(stringResource(Res.string.chat_attachment_error))
        AuroraComposerActivity(activity)
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = if (touch) AuroraLayout.ComposerMinRow else AuroraLayout.DesktopTarget),
            verticalAlignment = if (touch) Alignment.CenterVertically else Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (touch) AuroraLayout.SmallGap else AuroraLayout.Space),
        ) {
            attach()
            AuroraComposerField(
                visibleField, onEdit, onSend, touch, sendEnabled && !working, maxTextHeight,
                modifier = Modifier.weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .then(attachmentInput.pasteModifier),
                readOnly = submitting,
            )
            AuroraSendControl(working, if (working) !stopping else sendEnabled, touch) {
                if (working) onAction(AiStudioAction.CancelTurn) else onSend()
            }
        }
    }
}

@Composable
private fun AuroraComposerActivity(activity: Activity) {
    if (activity != Activity.Stopping) return
    AuroraText(
        "Останавливаем…",
        Modifier.padding(start = AuroraLayout.ContentInset, top = AuroraLayout.SmallGap)
            .semantics { liveRegion = LiveRegionMode.Polite },
        small = true, color = AuroraColors.Muted,
    )
}

@Composable
private fun AuroraComposerField(
    field: TextFieldValue,
    onEdit: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    touch: Boolean,
    canSend: Boolean,
    maxTextHeight: Dp,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val style = auroraMessageStyle()
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = field,
        onValueChange = onEdit,
        readOnly = readOnly,
        modifier = modifier.heightIn(max = if (touch) AuroraLayout.ComposerMaxText else maxTextHeight)
            .padding(vertical = if (touch) AuroraLayout.ControlGap else AuroraLayout.ControlGap - AuroraLayout.Stroke)
            .semantics { contentDescription = "Сообщение" }
            .onPreviewKeyEvent { event ->
                if (touch || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Tab) {
                    // The multiline editor otherwise treats Tab as text input.
                    focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                    return@onPreviewKeyEvent true
                }
                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                val handled = enter && !event.isShiftPressed && field.composition == null
                if (handled && canSend) onSend()
                handled
            },
        textStyle = style,
        cursorBrush = SolidColor(AuroraColors.Primary),
        decorationBox = { content ->
            Box {
                if (field.text.isEmpty()) {
                    BasicText(
                        if (touch) "Сообщение…" else "Сообщение",
                        style = style.copy(color = if (touch) AuroraColors.MessageMuted else AuroraColors.ComposerPlaceholder),
                    )
                }
                content()
            }
        },
    )
}
