package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlin.coroutines.cancellation.CancellationException
import kotlin.Throws as Throwable

internal interface PreparedChatSession {

    val projectId: ProjectId
    val engineId: String
    val sessionId: String
    val isAvailable: Boolean
    val settings: ChatEngineOptions

    @Throwable(CancellationException::class)
    suspend fun trackContextUsage(chatSessionId: String)

    @Throwable(Exception::class, CancellationException::class)
    suspend fun select(selector: String, value: String): ChatEngineOptions

    @Throwable(Exception::class, CancellationException::class)
    suspend fun close()
}
