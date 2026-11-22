package io.github.aequicor.heartbeat.features.aistudio.presentation.state

import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState

internal interface StudioWorkspaceStorage {

    suspend fun restore(legacy: suspend () -> AuroraWorkspace): AuroraWorkspace

    fun save(workspace: AuroraWorkspace, previousWorkStates: Map<String, SessionWorkState> = mapOf())

    fun load(chat: AuroraChat): AuroraChat
}
