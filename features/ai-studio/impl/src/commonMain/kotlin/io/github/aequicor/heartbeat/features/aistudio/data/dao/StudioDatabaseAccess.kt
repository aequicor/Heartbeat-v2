package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import kotlin.coroutines.cancellation.CancellationException

@Suppress("TooGenericExceptionCaught") // SQLDelight drivers report failures through platform-specific exceptions.
internal inline fun <T> accessStudioDatabase(operation: String, action: () -> T): T = try {
    action()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    throw DaoException(operation, failure)
}
