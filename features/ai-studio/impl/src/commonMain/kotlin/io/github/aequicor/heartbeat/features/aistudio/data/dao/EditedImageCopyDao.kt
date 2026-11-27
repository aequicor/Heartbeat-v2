package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface EditedImageCopyDao {

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun save(sessionId: String, id: String, name: String, description: String?, path: String, sizeBytes: Long, header: String)
}
