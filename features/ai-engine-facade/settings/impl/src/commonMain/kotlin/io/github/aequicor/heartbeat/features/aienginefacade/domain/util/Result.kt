package io.github.aequicor.heartbeat.features.aienginefacade.domain.util

import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Throwable(CancellationException::class)
internal fun <T> Result<T>.rethrowCancellation(): Result<T> {
    val failure = exceptionOrNull()
    if (failure is CancellationException) throw failure
    return this
}
