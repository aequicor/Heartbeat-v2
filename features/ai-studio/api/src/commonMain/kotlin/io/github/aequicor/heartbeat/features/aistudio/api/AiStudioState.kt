package io.github.aequicor.heartbeat.features.aistudio.api

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus

public data class AiStudioState(
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
    public val connected: Boolean get() = connection == AiEngineState.Ready

    public val turnActive: Boolean
        get() = session?.status.let { it == SessionStatus.Running || it is SessionStatus.AwaitingPermission }

    public val canManageChats: Boolean get() = connected && !busy && !turnActive

    public val canEditDraft: Boolean
        get() = canManageChats && session?.status.let { it == SessionStatus.Idle || it is SessionStatus.Failed }

    public val canSend: Boolean get() = canEditDraft && draft.isNotBlank()
}
