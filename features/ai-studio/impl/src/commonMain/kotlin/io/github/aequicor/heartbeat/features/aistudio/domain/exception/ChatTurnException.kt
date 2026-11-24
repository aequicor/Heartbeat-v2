package io.github.aequicor.heartbeat.features.aistudio.domain.exception

internal class ChatTurnException(
    message: String,
    cause: Throwable? = null,
    val notDispatched: Boolean = false,
) : Exception(message, cause)
