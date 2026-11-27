package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.model.PromptReference
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface FindPromptReferencesUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(sessionId: String?, projectId: String?, query: String): Result<List<PromptReference>>
}
