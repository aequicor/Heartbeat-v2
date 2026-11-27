@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
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
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraDraftReferences
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.PromptReferences
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
    maxReferenceHeight: Dp,
    modifier: Modifier = Modifier,
    inlineRequestStop: Boolean = true,
    onPanelHeight: (Int) -> Unit = {},
    onTail: () -> Unit = { list.requestScrollToItem(0) },
) {
    val chat = state.chat ?: return
    val chatId = chat.id
    val snapshot by draft.collectAsState()
    val references = snapshot.takeIf { it.chatId == chatId }?.references
    val text = snapshot.takeIf { it.chatId == chatId }?.text.orEmpty()
    var field by remember(chatId, draft) { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    var appliedDraft by remember(chatId, draft) { mutableStateOf(snapshot) }
    if (snapshot != appliedDraft) {
        appliedDraft = snapshot
        if (snapshot.chatId == chatId && snapshot.references?.hasQuotes != true && snapshot.text != field.text) {
            field = TextFieldValue(snapshot.text, TextRange(snapshot.text.length))
        }
    }
    val submitting = chatId in state.submittingDrafts
    val editing by rememberUpdatedState(state.request == null)
    val measuredPanel = Modifier.onSizeChanged { onPanelHeight(it.height) }
    val layout = if (touch) modifier.widthIn(max = AuroraLayout.Composer) else modifier
    Column(
        layout.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.ControlGap),
    ) {
        if (showTail) {
            AuroraTailControl(list, touch, onJump = onTail)
        }
        if (state.referenceUnavailable) {
            AuroraHeaderStorageError("Исходная версия недоступна")
        }
        val request = state.request
        if (request != null) {
            AuroraRequestPanel(
                request, touch, onAction, measuredPanel.heightIn(max = maxPanelHeight), inlineStop = inlineRequestStop,
            )
        } else {
            AuroraComposer(
                field = field,
                messageReferences = references,
                maxReferenceHeight = maxReferenceHeight,
                submitting = submitting,
                onEdit = {
                    val current = draft.value
                    if (current.chatId == chatId) {
                        // Echo edits before the next IME event, even if its draft snapshot has not been collected yet.
                        if (editing || it.text != current.text) field = it
                        if (it.text != current.text) onAction(AiStudioAction.EditDraft(chatId, it.text))
                    }
                },
                onRestoreCaret = { offset ->
                    val current = draft.value
                    if (current.chatId == chatId) {
                        field = TextFieldValue(current.text, TextRange(offset.coerceIn(0, current.text.length)))
                    }
                },
                activity = state.activity,
                sendBlocked = state.isPromptBlockedBySecurityMode(chat) || chatId in state.pendingAttachments,
                files = state.attachments,
                touch = touch,
                attachmentDraftId = chatId.takeIf { state.allowsFileImport && !submitting },
                importingAttachment = chatId in state.importingAttachmentsFor,
                attachmentError = state.attachmentErrorFor == chatId,
                attachmentErrorMessage = state.attachmentErrorMessage,
                referenceDraftId = chatId,
                referenceProjectId = chat.projectId,
                references = state.promptReferences,
                attach = {
                    if (state.allowsFileImport) {
                        AuroraAttachmentPickerButton(
                            chatId, chatId in state.importingAttachmentsFor, touch, onAction,
                            enabled = !submitting && chatId !in state.pendingAttachments,
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
            sendBlocked = bootstrap.id in state.pendingAttachments ||
                bootstrap.operation != null || bootstrap.picker.confirmed == null || bootstrap.picker.step != null ||
                bootstrap.picker.securityModeBusy || bootstrap.picker.securityModeError != null || bootstrap.picker.securityMode?.requiresUserChoice == true,
            attachmentDraftId = bootstrap.id.takeIf { state.allowsFileImport && bootstrap.operation == null },
            importingAttachment = bootstrap.id in state.importingAttachmentsFor,
            attachmentError = state.attachmentErrorFor == bootstrap.id,
            attachmentErrorMessage = state.attachmentErrorMessage,
            referenceDraftId = bootstrap.id,
            referenceProjectId = (bootstrap.project as? SessionBootstrapState.Project.Existing)?.id,
            references = state.promptReferences,
            attach = {
                if (state.allowsFileImport) {
                    AuroraAttachmentPickerButton(
                        bootstrap.id, bootstrap.id in state.importingAttachmentsFor, touch, onAction,
                        enabled = bootstrap.operation == null && bootstrap.id !in state.pendingAttachments,
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
    attachmentErrorMessage: String? = null,
    referenceDraftId: String,
    referenceProjectId: String?,
    references: PromptReferences?,
    messageReferences: AuroraDraftReferences? = null,
    maxReferenceHeight: Dp = maxTextHeight,
    onRestoreCaret: (Int) -> Unit = {},
    attach: @Composable () -> Unit,
) {
    val working = activity != Activity.Idle
    val stopping = activity == Activity.Stopping
    val invalidImageName = files.any { it.hasInvalidImageExtension() }
    val sendEnabled = !submitting && !sendBlocked && !importingAttachment && !invalidImageName &&
        (field.text.isNotBlank() || messageReferences?.hasQuotes == true || files.isNotEmpty())
    val visibleField = if (submitting) TextFieldValue() else field
    val onSend = { onAction(AiStudioAction.Send) }
    val interaction = LocalAuroraReferenceInteraction.current
    val requester = remember { FocusRequester() }
    val quoteReferences = messageReferences?.takeIf { it.hasQuotes && !submitting }
    val partCount = quoteReferences?.nodes?.size ?: 1
    val request = interaction?.requestedFocus(partCount)
    SideEffect { interaction?.editorParts = partCount }
    LaunchedEffect(referenceDraftId) {
        if (partCount == 1) interaction?.position = AuroraReferenceInteraction.Cursor(0, field.selection.end)
    }
    LaunchedEffect(request, partCount) {
        if (request != null && partCount == 1) {
            onRestoreCaret(request.offset)
            requester.requestFocus()
            interaction.position = request
            interaction.focusHandled()
        }
    }
    val attachmentInput = rememberAttachmentInput(attachmentDraftId, importingAttachment, onAction)
    var editedAttachmentId by remember(referenceDraftId) { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    var keepOutlineHidden by remember { mutableStateOf(false) }
    val keyboardNavigation = HbFocusIndication.keyboardNavigation
    val showFocusOutline = !touch && focused && !keepOutlineHidden && keyboardNavigation
    val surface = if (touch) {
        Modifier.background(AuroraColors.Pearl, RoundedCornerShape(AuroraLayout.ComposerRadius))
            .padding(horizontal = AuroraLayout.ControlGap, vertical = AuroraLayout.SmallGap)
    } else {
        Modifier.auroraComposerSurface(showFocusOutline)
            .padding(AuroraLayout.ControlGap)
    }

    Column(
        modifier.fillMaxWidth().then(attachmentInput.dropModifier).then(surface)
            .then(if (messageReferences != null) Modifier.heightIn(max = maxReferenceHeight) else Modifier),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
    ) {
        if (!submitting && files.isNotEmpty()) {
            AuroraDraftAttachments(files, touch, { editedAttachmentId = it.id }, onAction)
        }
        if (attachmentError) AuroraHeaderStorageError(
            attachmentErrorMessage ?: stringResource(Res.string.chat_attachment_error),
        )
        AuroraComposerActivity(activity)
        if (!submitting) messageReferences?.reply?.let { reference ->
            AuroraReplyReference(
                reference, onOpen = { interaction?.jump(reference) },
                onRemove = { onAction(AiStudioAction.MessageReference.DeleteReply(referenceDraftId)) },
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = if (touch) AuroraLayout.ComposerMinRow else AuroraLayout.DesktopTarget),
            verticalAlignment = if (touch) Alignment.CenterVertically else Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (touch) AuroraLayout.SmallGap else AuroraLayout.Space),
        ) {
            attach()
            if (quoteReferences != null) {
                AuroraReferenceEditor(
                    quoteReferences, referenceDraftId, referenceProjectId, touch, maxReferenceHeight,
                    sendEnabled && !working, onSend, onAction, references,
                    modifier = Modifier.weight(1f), fieldModifier = attachmentInput.pasteModifier,
                )
            } else AuroraComposerField(
                visibleField,
                {
                    interaction?.position = AuroraReferenceInteraction.Cursor(0, it.selection.end)
                    onEdit(it)
                },
                onSend, touch, sendEnabled && !working, maxTextHeight,
                modifier = Modifier.weight(1f).focusRequester(requester)
                    .onFocusChanged {
                        if (it.isFocused && !focused) keepOutlineHidden = !keyboardNavigation
                        focused = it.isFocused
                        if (focused) interaction?.position = AuroraReferenceInteraction.Cursor(0, field.selection.end)
                        else keepOutlineHidden = false
                    }
                    .then(attachmentInput.pasteModifier),
                readOnly = submitting,
                focused = focused,
                referenceDraftId = referenceDraftId,
                referenceProjectId = referenceProjectId,
                references = references,
                onAction = onAction,
                onNavigateReferences = {
                    // An arrow handled by the popup keeps the editor's current focus appearance.
                    if (!touch && !showFocusOutline) keepOutlineHidden = true
                },
            )
            AuroraSendControl(working, if (working) !stopping else sendEnabled, touch) {
                if (working) onAction(AiStudioAction.CancelTurn) else onSend()
            }
        }
    }
    if (!submitting) files.firstOrNull { it.id == editedAttachmentId }?.let { file ->
        key(referenceDraftId, file.id) {
            AuroraDraftAttachmentEditor(
                file, onDismiss = { editedAttachmentId = null },
                onSave = { name, description, deferred, edit, thumbnail ->
                    onAction(AiStudioAction.UpdateDraftAttachment(referenceDraftId, file.id, name, description, deferred, edit, thumbnail))
                    editedAttachmentId = null
                },
            )
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
