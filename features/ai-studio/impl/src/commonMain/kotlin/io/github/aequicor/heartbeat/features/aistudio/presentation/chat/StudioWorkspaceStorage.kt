package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface StudioWorkspaceStorage : StudioWorkspaceMetadata {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun load(chat: AuroraChat): AuroraChat

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun loadOlder(chat: AuroraChat): AuroraChat

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun openFile(attachmentId: String): File

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun removeUnavailableAttachment(sessionId: String, snapshotId: String)

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun readPartBody(partId: Long): String
}
