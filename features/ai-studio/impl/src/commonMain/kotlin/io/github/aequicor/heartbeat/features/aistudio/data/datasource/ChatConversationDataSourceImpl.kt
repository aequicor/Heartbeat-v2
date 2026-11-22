package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatConversationDao
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConversation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class ChatConversationDataSourceImpl(private val dao: ChatConversationDao) : ChatConversationDataSource {

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun find(sessionId: String): ChatConversation? = accessStudioRepository {
        dao.find(sessionId)?.let { record ->
            val config = record.engineId?.let { ChatConfiguration(it, record.model, record.reasoning) }
            ChatConversation(config, record.sessionId, record.stale != 0L)
        }
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun save(sessionId: String, messages: List<ChatHistoryEntry.Message>) = accessStudioRepository {
        dao.save(sessionId, messages.map { it.toTurnRecord() })
    }
}
