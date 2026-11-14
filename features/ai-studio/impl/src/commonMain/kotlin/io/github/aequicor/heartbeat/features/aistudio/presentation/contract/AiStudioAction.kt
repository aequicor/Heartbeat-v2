package io.github.aequicor.heartbeat.features.aistudio.presentation.contract

import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey

public sealed interface AiStudioAction {
    public data class EditDirectory(val value: String) : AiStudioAction
    public data class EditDraft(val value: String) : AiStudioAction
    public data object Connect : AiStudioAction
    public data object Disconnect : AiStudioAction
    public data object RefreshSessions : AiStudioAction
    public data object LoadMoreSessions : AiStudioAction
    public data object NewChat : AiStudioAction
    public data class OpenChat(val key: SessionKey) : AiStudioAction
    public data object Send : AiStudioAction
    public data object CancelTurn : AiStudioAction
    public data class AnswerPermission(val requestId: String, val optionId: String) : AiStudioAction
}
