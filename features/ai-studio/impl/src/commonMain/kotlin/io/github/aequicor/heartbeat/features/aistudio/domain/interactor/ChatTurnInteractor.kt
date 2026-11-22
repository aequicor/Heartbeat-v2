package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatTurnInteractor {

    @Throwable(CancellationException::class)
    suspend fun send(
        sessionId: String,
        text: String,
        attachments: List<ChatAttachment>,
        cancellationRequested: () -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun cancel(sessionId: String): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun answerPermission(sessionId: String, requestId: String, optionId: String): Result<Unit>
}
