package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aiengine.api.session.QuestionnaireAnswer
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatTurnInteractor {

    @Throwable(CancellationException::class)
    suspend fun send(
        sessionId: String,
        messageId: String,
        text: String,
        attachments: List<ChatAttachment>,
        expectedDraftRevision: String,
        cancellationRequested: () -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun retry(
        sessionId: String,
        messageId: String,
        expectedFailedRevisionId: Long,
        cancellationRequested: () -> Boolean,
        update: suspend (ChatTurn) -> Unit,
    ): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun selectSecurityMode(sessionId: String, value: String?): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun cancel(sessionId: String): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun applyConfiguration(sessionId: String, configuration: ChatConfiguration): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun answerPermission(sessionId: String, requestId: String, optionId: String): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun answerQuestionnaire(sessionId: String, requestId: String, answer: QuestionnaireAnswer): Result<Unit>
}
