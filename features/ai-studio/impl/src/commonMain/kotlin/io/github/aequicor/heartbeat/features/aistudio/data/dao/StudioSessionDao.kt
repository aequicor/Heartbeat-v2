package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.data.model.StudioSessionDto
import kotlin.coroutines.cancellation.CancellationException

internal interface StudioSessionDao {
    /** Joins an existing SQLDelight transaction on the same connection. */
    @Throws(DaoException::class, CancellationException::class)
    suspend fun start(
        projectId: String,
        title: String,
        sentAt: DatabaseTimestamp,
        parentSessionId: String? = null,
    ): StudioSessionDto

    @Throws(DaoException::class, CancellationException::class)
    suspend fun findById(id: String): StudioSessionDto?

    @Throws(DaoException::class, CancellationException::class)
    suspend fun listRoots(
        projectId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor? = null,
    ): DaoPage<StudioSessionDto>

    @Throws(DaoException::class, CancellationException::class)
    suspend fun listChildren(
        parentSessionId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor? = null,
    ): DaoPage<StudioSessionDto>

    @Throws(DaoException::class, CancellationException::class)
    suspend fun rename(id: String, title: String)

    @Throws(DaoException::class, CancellationException::class)
    suspend fun setExpanded(id: String, isExpanded: Boolean)

    @Throws(DaoException::class, CancellationException::class)
    suspend fun setStatus(id: String, status: String, changedAt: DatabaseTimestamp)
}
