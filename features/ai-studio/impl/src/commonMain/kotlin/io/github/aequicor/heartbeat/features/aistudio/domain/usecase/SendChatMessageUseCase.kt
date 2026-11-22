package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface SendChatMessageUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(
        sessionId: String,
        text: String,
        attachments: List<ChatAttachment>,
        started: suspend (ChatEngineConversationDataSource) -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit>
}
