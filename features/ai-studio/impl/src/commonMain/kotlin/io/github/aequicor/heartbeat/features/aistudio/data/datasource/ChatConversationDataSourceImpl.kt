package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aiengine.api.session.ContextWindowUsage
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatConversationDao
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConfigRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConversationRecord
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConversation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSecurityMode
import kotlinx.serialization.json.Json
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class ChatConversationDataSourceImpl(private val dao: ChatConversationDao) : ChatConversationDataSource {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun find(sessionId: String): ChatConversation? = accessStudioRepository {
        dao.find(sessionId)?.let { record ->
            val securityMode = record.securityModeJson?.let { Json.decodeFromString<ChatSecurityMode>(it) }
            val config = record.engineId?.let { ChatConfiguration(it, record.model, record.reasoning, securityMode) }
            val usage = if (record.used != null && record.size != null) ContextWindowUsage(record.used, record.size) else null
            ChatConversation(config, record.sessionId, record.stale != 0L, usage)
        }
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun updateConfiguration(
        sessionId: String,
        expected: ChatConversation,
        configuration: ChatConfiguration,
    ): Boolean = accessStudioRepository {
        val old = expected.configuration
        dao.updateConfiguration(
            sessionId,
            ChatConversationRecord(old?.engineId, old?.model, old?.reasoning, expected.engineSessionId, if (expected.contextStale) 1 else 0),
            ChatConfigRecord(configuration.engineId, configuration.model, configuration.reasoning),
        )
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun save(sessionId: String, messages: List<ChatHistoryEntry.Message>) = accessStudioRepository {
        dao.save(sessionId, messages.map { it.toTurnRecord() })
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun commitUserPrompt(
        sessionId: String,
        message: ChatHistoryEntry.User,
        expectedDraftRevision: String,
    ): ChatHistoryEntry.User? =
        accessStudioRepository {
            dao.commitUserPrompt(sessionId, message.toTurnRecord(), expectedDraftRevision)?.let {
                it.toDomain(keepAttachmentReference = true).message as ChatHistoryEntry.User
            }
        }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun reserveRetry(
        sessionId: String,
        messageId: String,
        expectedFailedRevisionId: Long,
    ): ChatHistoryEntry.User? = accessStudioRepository {
        dao.reserveRetry(sessionId, messageId, expectedFailedRevisionId)?.let {
            it.toDomain(keepAttachmentReference = true).message as ChatHistoryEntry.User
        }
    }
}
