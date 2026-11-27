package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aiengine.api.session.QuestionnaireAnswer
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.AttachmentTransferException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.BackgroundChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentTransferMode
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SentMessageAttachment
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatEngineConversationDataSource {

    val settings: ChatEngineOptions

    @Throwable(AttachmentTransferException::class, CancellationException::class)
    suspend fun resolveAttachmentUri(file: AttachmentFile, transferMode: AttachmentTransferMode): String

    @Throwable(Exception::class, CancellationException::class)
    suspend fun select(id: String, value: String): ChatEngineOptions

    @Throwable(Exception::class, CancellationException::class)
    suspend fun send(
        text: String,
        snapshots: List<SentMessageAttachment>,
        update: suspend (ChatEngineTurn) -> Unit,
    )

    @Throwable(Exception::class, CancellationException::class)
    suspend fun observeBackground(update: suspend (BackgroundChatTurn) -> Unit)

    @Throwable(CancellationException::class)
    suspend fun cancel()

    @Throwable(Exception::class, CancellationException::class)
    suspend fun close()

    fun answerPermission(requestId: String, optionId: String)

    fun answerQuestionnaire(requestId: String, answer: QuestionnaireAnswer)
}
