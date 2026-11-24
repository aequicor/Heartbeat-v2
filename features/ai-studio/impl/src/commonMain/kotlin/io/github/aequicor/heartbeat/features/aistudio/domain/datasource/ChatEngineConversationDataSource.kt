package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineTurn
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatEngineConversationDataSource {

    val settings: ChatEngineOptions

    @Throwable(Exception::class, CancellationException::class)
    suspend fun select(id: String, value: String): ChatEngineOptions

    @Throwable(Exception::class, CancellationException::class)
    suspend fun send(text: String, files: List<ChatAttachment>, update: suspend (ChatEngineTurn) -> Unit)

    @Throwable(CancellationException::class)
    suspend fun cancel()

    @Throwable(Exception::class, CancellationException::class)
    suspend fun close()

    fun answerPermission(requestId: String, optionId: String)
}
