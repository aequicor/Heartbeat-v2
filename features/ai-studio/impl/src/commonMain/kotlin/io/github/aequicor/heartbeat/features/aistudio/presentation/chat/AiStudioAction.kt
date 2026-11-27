package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Scenario
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState

public sealed interface AiStudioAction {
    public data class EditDraft(val chatId: String, val text: String) : AiStudioAction
    public data class ReadAt(val chatId: String, val anchor: Anchor) : AiStudioAction
    public data class LoadOlderMessages(val chatId: String, val before: Long) : AiStudioAction
    public data class OpenChat(val id: String) : AiStudioAction
    public data class ArchiveChat(val id: String) : AiStudioAction
    public data class RenameSession(val id: String, val title: String) : AiStudioAction
    public data class DeleteSession(val id: String) : AiStudioAction
    public data class SearchChats(val text: String) : AiStudioAction
    public data object ExpandChatSearch : AiStudioAction
    public data object CollapseChatSearch : AiStudioAction
    public data object CreateSessionWithoutProject : AiStudioAction
    public data object CreateSession : AiStudioAction
    public data class CreateSessionInProject(val id: String) : AiStudioAction
    public sealed interface EnginePicker : AiStudioAction {

        public data class Open(val step: ChatEnginePickerState.Step) : EnginePicker
        public data class Engine(val id: String) : EnginePicker
        public data class Model(val id: String) : EnginePicker
        public data class Reasoning(val id: String) : EnginePicker
        public data object Apply : EnginePicker
        public data object Back : EnginePicker
        public data object Dismiss : EnginePicker
        public data object Retry : EnginePicker
    }

    public data object LoadBootstrapProjects : AiStudioAction
    public data object DismissSessionBootstrap : AiStudioAction
    public sealed interface BootstrapProject : AiStudioAction {
        public data class Add(val workingDirectoryPath: String, val displayName: String? = null) : BootstrapProject
        public data class Select(
            val project: SessionBootstrapState.Project,
            val title: String? = null,
        ) : BootstrapProject
        public data class Retry(val name: String) : BootstrapProject
        public data object Cancel : BootstrapProject
    }
    public data class SubmitSessionBootstrap(
        val project: SessionBootstrapState.Project,
        val prompt: String,
        val scenario: Scenario = Scenario.Standard,
        val access: Access = Access.Workspace,
        val effort: String? = null,
    ) : AiStudioAction
    public data class RenameProject(val id: String, val title: String) : AiStudioAction
    public data class DeleteProject(val id: String) : AiStudioAction
    public data class NewProjectChat(val project: String) : AiStudioAction
    public data class SetScenario(val scenario: Scenario) : AiStudioAction
    public data class SelectSecurityMode(val chatId: String, val value: String?) : AiStudioAction

    public data class SetAccess(val access: Access) : AiStudioAction
    public data class SetEffort(val effort: String) : AiStudioAction
    public data class Attach(val fileId: String) : AiStudioAction
    public data class ImportAttachment(val draftId: String, val reference: String) : AiStudioAction
    public data class ImportAttachments(
        val draftId: String,
        val source: AttachmentImportSource,
        val origin: Origin = Origin.File,
    ) : AiStudioAction {

        public enum class Origin { File, ClipboardFile, ClipboardImage }
    }
    public data class AttachmentPickerFailed(val draftId: String) : AiStudioAction
    public data class ConfirmAttachment(
        val draftId: String,
        val fileId: String,
        val name: String,
        val description: String?,
        val deferredReading: Boolean,
        val editedImage: AttachmentImportSource? = null,
    ) : AiStudioAction

    public data class CancelAttachment(val draftId: String, val fileId: String) : AiStudioAction
    public data class UpdateDraftAttachment(
        val draftId: String,
        val fileId: String,
        val name: String,
        val description: String?,
        val deferredReading: Boolean,
        val editedImage: AttachmentImportSource? = null,
    ) : AiStudioAction
    public data class SaveViewedImageCopy(
        val sessionId: String,
        val fileId: String,
        val source: AttachmentImportSource,
    ) : AiStudioAction

    public data class Detach(val fileId: String) : AiStudioAction
    public data class EditMessage(val id: String) : AiStudioAction
    public data class Retry(val id: String, val expectedFailedRevisionId: Long? = null) : AiStudioAction
    public data class InteractWithRequest(val requestId: String) : AiStudioAction
    public data class Choose(val requestId: String, val questionId: String, val option: String) : AiStudioAction
    public data class EditAnswer(val requestId: String, val questionId: String, val text: String) : AiStudioAction
    public data class MoveQuestion(val requestId: String, val offset: Int) : AiStudioAction
    public data class SkipQuestion(val requestId: String) : AiStudioAction
    public data class SubmitAnswers(val requestId: String) : AiStudioAction
    public data class DeclineQuestionnaire(val requestId: String) : AiStudioAction
    public data class AnswerPermission(val requestId: String, val allow: Boolean, val chatId: String? = null) : AiStudioAction
    public data class OpenFile(val id: String) : AiStudioAction
    public data class SearchPromptReferences(val draftId: String, val projectId: String?, val query: String?) : AiStudioAction
    public data class RemoveUnavailableFile(val sessionId: String, val id: String) : AiStudioAction
    public data class OpenResult(val id: String) : AiStudioAction
    public data class OpenArtifact(val replyId: String, val fileId: String) : AiStudioAction
    public data class CopyFile(val id: String) : AiStudioAction
    public data class SaveFile(val id: String) : AiStudioAction
    public data class SaveArtifact(val replyId: String, val fileId: String) : AiStudioAction
    public data class FileCopyFinished(val failed: Boolean) : AiStudioAction
    public data class ArtifactSaveFinished(val failed: Boolean) : AiStudioAction
    public data class ToggleWork(val id: String) : AiStudioAction
    public data class ToggleStep(val replyId: String, val stepId: String) : AiStudioAction
    public data class ToggleWorkEvent(val replyId: String, val stepId: String, val eventId: String) : AiStudioAction
    public data class SetWorkExpanded(
        val replyId: String, val expanded: Boolean, val stepId: String? = null, val eventId: String? = null,
    ) : AiStudioAction
    public data object ExpandChats : AiStudioAction
    public data object CollapseChats : AiStudioAction
    public data class ToggleFiles(val visible: Boolean) : AiStudioAction
    public data class ShowFileSearch(val visible: Boolean) : AiStudioAction
    public data class SearchFiles(val text: String) : AiStudioAction
    public data object CloseViewer : AiStudioAction
    public data object NewChat : AiStudioAction
    public data object Send : AiStudioAction
    public data class SendChatDraft(val chatId: String) : AiStudioAction
    public data object CancelTurn : AiStudioAction
    public data class CancelChatTurn(val chatId: String) : AiStudioAction
}
