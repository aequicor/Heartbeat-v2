package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import kotlin.time.Instant

internal inline fun <T> accessStudioRepository(action: () -> T): T = try {
    action()
} catch (failure: DaoException) {
    throw StudioRepositoryException(failure.message.orEmpty(), failure.cause ?: failure)
}

internal fun Instant.toDatabaseTimestamp() = DatabaseTimestamp(epochSeconds, nanosecondsOfSecond.toLong())

internal fun DatabaseTimestamp.toInstant(): Instant = Instant.fromEpochSeconds(epochSeconds, nanoseconds)

internal fun <Id> PageCursor<Id>.toDaoCursor(encodeId: (Id) -> String) =
    DaoPageCursor(updatedAt = updatedAt.toDatabaseTimestamp(), id = encodeId(id))

internal fun <Id> DaoPageCursor.toPageCursor(decodeId: (String) -> Id): PageCursor<Id> =
    PageCursor(updatedAt = updatedAt.toInstant(), id = decodeId(id))
