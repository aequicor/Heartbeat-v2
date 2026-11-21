package io.github.aequicor.heartbeat.features.aistudio.presentation.contract

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Scenario

public sealed interface AiStudioAction {
    public data class EditDraft(val chatId: String, val text: String) : AiStudioAction
    public data class ReadAt(val chatId: String, val anchor: Anchor) : AiStudioAction
    public data class OpenChat(val id: String) : AiStudioAction
    public data class ArchiveChat(val id: String) : AiStudioAction
    public data class SearchChats(val text: String) : AiStudioAction
    public data class ShowChatSearch(val visible: Boolean) : AiStudioAction
    public data class ToggleNavigation(val id: String) : AiStudioAction
    public data class NavigationFocusHandled(val id: String) : AiStudioAction
    /** Opens a local project by its absolute working directory, including projects with no chats. */
    public data class OpenProject(val workingDirectory: String, val name: String) : AiStudioAction
    public data class NewProjectChat(val project: String, val engineId: String? = null) : AiStudioAction
    public data class SetScenario(val scenario: Scenario) : AiStudioAction
    public data class SetAccess(val access: Access) : AiStudioAction
    public data class SetEffort(val effort: String) : AiStudioAction
    public data class Attach(val fileId: String) : AiStudioAction
    public data class Detach(val fileId: String) : AiStudioAction
    public data class EditMessage(val id: String) : AiStudioAction
    public data class Retry(val id: String) : AiStudioAction
    public data class InteractWithRequest(val requestId: String) : AiStudioAction
    public data class Choose(val requestId: String, val questionId: String, val option: String) : AiStudioAction
    public data class EditAnswer(val requestId: String, val questionId: String, val text: String) : AiStudioAction
    public data class MoveQuestion(val requestId: String, val offset: Int) : AiStudioAction
    public data class SkipQuestion(val requestId: String) : AiStudioAction
    public data class SubmitAnswers(val requestId: String) : AiStudioAction
    public data class AnswerPermission(val requestId: String, val allow: Boolean) : AiStudioAction
    public data class OpenFile(val id: String) : AiStudioAction
    public data class OpenResult(val id: String) : AiStudioAction
    public data class ToggleWork(val id: String) : AiStudioAction
    public data class ToggleStep(val replyId: String, val stepId: String) : AiStudioAction
    public data class ToggleWorkEvent(val replyId: String, val stepId: String, val eventId: String) : AiStudioAction
    public data class ToggleChats(val visible: Boolean) : AiStudioAction
    public data class ToggleFiles(val visible: Boolean) : AiStudioAction
    public data class ShowFileSearch(val visible: Boolean) : AiStudioAction
    public data class SearchFiles(val text: String) : AiStudioAction
    public data object CloseViewer : AiStudioAction
    public data object NewChat : AiStudioAction
    public data object Send : AiStudioAction
    public data object CancelTurn : AiStudioAction
}
