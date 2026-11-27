package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface StudioWorkspaceStorage : StudioWorkspaceMetadata {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun load(chat: AuroraChat): AuroraChat

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun referenceSource(sessionId: String, displayId: String?, revisionId: Long? = null): ChatHistoryEntry?

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun loadThrough(chat: AuroraChat, displayId: String): AuroraChat

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun loadOlder(chat: AuroraChat): AuroraChat

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun openFile(attachmentId: String): File

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun removeUnavailableAttachment(sessionId: String, snapshotId: String)

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun readPartBody(partId: Long): String
}
