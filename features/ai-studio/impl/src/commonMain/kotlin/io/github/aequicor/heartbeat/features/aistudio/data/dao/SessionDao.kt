package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.data.model.SessionDto
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConfigRecord
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface SessionDao {

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun <T> transaction(block: suspend () -> T): T

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun lastConfiguration(): ChatConfigRecord?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun rememberSecurityMode(configuration: ChatConfigRecord)

    /** Joins an existing SQLDelight transaction on the same connection. */
    @Throwable(DaoException::class, CancellationException::class)
    suspend fun insert(
        projectId: String,
        title: String,
        sentAt: DatabaseTimestamp,
        parentSessionId: String? = null,
        conversation: ChatConfigRecord.Conversation? = null,
    ): SessionDto

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun selectById(id: String): SessionDto?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun selectRoots(
        projectId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor? = null,
    ): DaoPage<SessionDto>

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun selectChildren(
        parentSessionId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor? = null,
    ): DaoPage<SessionDto>

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun updateTitle(id: String, title: String)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun updateExpanded(id: String, isExpanded: Boolean)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun updateStatus(id: String, status: String, changedAt: DatabaseTimestamp)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun delete(id: String)
}
