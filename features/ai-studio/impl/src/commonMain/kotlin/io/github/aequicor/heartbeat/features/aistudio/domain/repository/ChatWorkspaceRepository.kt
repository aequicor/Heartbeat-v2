package io.github.aequicor.heartbeat.features.aistudio.domain.repository

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatWorkspace
import io.github.aequicor.heartbeat.features.aistudio.domain.model.WorkspaceChat

internal interface ChatWorkspaceRepository<C : WorkspaceChat> {
    suspend fun load(): ChatWorkspace<C>?
    suspend fun save(workspace: ChatWorkspace<C>)
}
