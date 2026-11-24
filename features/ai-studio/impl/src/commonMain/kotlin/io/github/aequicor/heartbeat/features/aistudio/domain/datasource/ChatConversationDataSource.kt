package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConversation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatConversationDataSource {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun find(sessionId: String): ChatConversation?

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun updateConfiguration(sessionId: String, expected: ChatConversation, configuration: ChatConfiguration): Boolean

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun save(sessionId: String, messages: List<ChatHistoryEntry.Message>)

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun reserveRetry(sessionId: String, messageId: String): ChatHistoryEntry.User?
}
