package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aiengine.api.session.ContextWindowUsage
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConfigRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConversationRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSecurityMode
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatConversationDao {

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun find(sessionId: String): ChatConversationRecord?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun updateSecurityMode(
        chatId: String, engineId: String, engineSessionId: String?, transform: (ChatSecurityMode?) -> ChatSecurityMode?,
    ): ChatSecurityMode?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun saveContextUsage(chatSessionId: String, engineSession: SessionKey, usage: ContextWindowUsage)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun updateConfiguration(sessionId: String, expected: ChatConversationRecord, configuration: ChatConfigRecord): Boolean

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun save(sessionId: String, messages: List<ChatTurnMessageRecord>)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun commitUserPrompt(
        sessionId: String,
        message: ChatTurnMessageRecord,
        expectedDraftRevision: String,
    ): ChatEntryRecord?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun reserveRetry(sessionId: String, messageId: String, expectedFailedRevisionId: Long): ChatEntryRecord?
}
