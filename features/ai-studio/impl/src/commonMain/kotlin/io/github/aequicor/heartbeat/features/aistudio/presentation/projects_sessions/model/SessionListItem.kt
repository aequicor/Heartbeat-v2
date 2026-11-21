package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model

import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus

internal data class SessionListItem(
    val id: StudioSessionId,
    val title: String,
    val children: List<SessionListItem> = listOf(),
    val activityBadge: NavigationStatus? = null,
    val selected: Boolean = false,
    val matchesSearch: Boolean = true,
    val expanded: Boolean = true,
)
