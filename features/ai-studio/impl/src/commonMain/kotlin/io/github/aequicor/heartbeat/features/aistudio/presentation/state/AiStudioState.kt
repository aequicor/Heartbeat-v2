package io.github.aequicor.heartbeat.features.aistudio.presentation.state

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState

internal data class AiStudioState(
    val engineAvailable: Boolean = false,
    val connection: AiEngineState = AiEngineState.NotInitialized,
    val directory: String = "",
    val sessions: List<SessionKey> = listOf(),
    val nextCursor: String? = null,
    val selected: SessionKey? = null,
    val session: SessionState? = null,
    val draft: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val connected: Boolean get() = connection == AiEngineState.Ready

    val turnActive: Boolean
        get() = session?.status.let { it == SessionStatus.Running || it is SessionStatus.AwaitingPermission }

    val canManageChats: Boolean get() = connected && !busy && !turnActive

    val canEditDraft: Boolean
        get() = canManageChats && session?.status.let { it == SessionStatus.Idle || it is SessionStatus.Failed }

    val canSend: Boolean get() = canEditDraft && draft.isNotBlank()

    fun toScreenState(): AiStudioScreenState = AiStudioScreenState(
        engineAvailable = engineAvailable,
        connection = connection,
        directory = directory,
        sessions = sessions,
        hasMoreSessions = nextCursor != null,
        selected = selected,
        sessionStatus = session?.status,
        timeline = session?.messages?.items.orEmpty(),
        draft = draft,
        busy = busy,
        error = error,
        turnActive = turnActive,
        canManageChats = canManageChats,
        canEditDraft = canEditDraft,
        canSend = canSend,
    )
}
