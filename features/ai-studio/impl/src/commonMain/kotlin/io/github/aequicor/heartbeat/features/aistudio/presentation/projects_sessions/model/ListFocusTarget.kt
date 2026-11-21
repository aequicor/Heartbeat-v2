package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId

internal sealed interface ListFocusTarget {

    data class Project(val id: ProjectId) : ListFocusTarget
    data class Session(val id: StudioSessionId) : ListFocusTarget
}
