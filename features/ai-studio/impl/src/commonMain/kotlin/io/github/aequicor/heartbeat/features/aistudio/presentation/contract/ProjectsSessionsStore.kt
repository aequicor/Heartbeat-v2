package io.github.aequicor.heartbeat.features.aistudio.presentation.contract

import kotlinx.coroutines.flow.StateFlow

public interface ProjectsSessionsStore {

    public val state: StateFlow<ProjectsSessionsState>
    public suspend fun dispatch(action: ProjectsSessionsAction): Result<Unit>
}
