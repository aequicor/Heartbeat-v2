package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConversationRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatConversationDao {

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun find(sessionId: String): ChatConversationRecord?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun save(sessionId: String, messages: List<ChatTurnMessageRecord>)
}
