package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface StudioWorkspaceMetadata {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun restore(legacy: suspend () -> AuroraWorkspace): AuroraWorkspace

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun save(
        workspace: AuroraWorkspace,
        previousWorkStates: Map<String, SessionWorkState> = mapOf(),
        previousChats: Map<String, AuroraChat> = mapOf(),
    )

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun loadMetadata(chat: AuroraChat): AuroraChat
}
