package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface ApplyChatConfigurationUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(sessionId: String, configuration: ChatConfiguration): Result<Unit>
}
