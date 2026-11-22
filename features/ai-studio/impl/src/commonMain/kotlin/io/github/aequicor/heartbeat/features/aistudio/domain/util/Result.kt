package io.github.aequicor.heartbeat.features.aistudio.domain.util

import kotlin.coroutines.cancellation.CancellationException
import kotlin.Throws as Throwable

@Throwable(CancellationException::class)
internal fun <T> Result<T>.rethrowCancellation(): Result<T> {
    val error = exceptionOrNull()
    if (error is CancellationException) throw error
    return this
}
