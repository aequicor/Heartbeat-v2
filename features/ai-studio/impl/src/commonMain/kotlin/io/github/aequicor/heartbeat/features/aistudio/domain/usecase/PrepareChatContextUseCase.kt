package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface PrepareChatContextUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(sessionId: String): Result<ChatEngineConversationDataSource>
}
