package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId

internal data class ProjectListItem(
    val id: ProjectId,
    val title: String,
    val sessions: List<SessionListItem>,
    val expanded: Boolean = true,
    val showAllSessions: Boolean = false,
) {

    val hasMore: Boolean get() = expanded && !showAllSessions && sessions.size > SessionLimit

    val visibleSessions: List<SessionListItem>
        get() = when {
            !expanded -> listOf()
            showAllSessions -> sessions
            else -> sessions.take(SessionLimit)
        }

    private companion object {

        const val SessionLimit = 5
    }
}
