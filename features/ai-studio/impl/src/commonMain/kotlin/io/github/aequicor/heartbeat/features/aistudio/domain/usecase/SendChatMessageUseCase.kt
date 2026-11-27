package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatMessageBody
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface SendChatMessageUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(
        sessionId: String,
        messageId: String,
        text: String,
        attachments: List<ChatAttachment>,
        expectedDraftRevision: String,
        messageBody: ChatMessageBody? = null,
        started: suspend (ChatEngineConversationDataSource) -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun retry(
        sessionId: String,
        messageId: String,
        expectedFailedRevisionId: Long,
        started: suspend (ChatEngineConversationDataSource) -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit>
}
