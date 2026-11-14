package io.github.aequicor.heartbeat.features.aistudio.presentation.contract

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem

/** Read-only workspace snapshot for the AI Studio adapters. */
public data class AiStudioScreenState(
    val engineAvailable: Boolean = false,
    val connection: AiEngineState = AiEngineState.NotInitialized,
    val directory: String = "",
    val sessions: List<SessionKey> = listOf(),
    val hasMoreSessions: Boolean = false,
    val selected: SessionKey? = null,
    val sessionStatus: SessionStatus? = null,
    val timeline: List<SessionTimelineItem> = listOf(),
    val draft: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val turnActive: Boolean = false,
    val canManageChats: Boolean = false,
    val canEditDraft: Boolean = false,
    val canSend: Boolean = false,
) {
    val connected: Boolean get() = connection == AiEngineState.Ready
}
