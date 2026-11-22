package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatDraftState
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import kotlinx.coroutines.flow.StateFlow
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatCreationDraft {

    val state: StateFlow<ChatDraftState>

    @Throwable(CancellationException::class)
    suspend fun chooseEngine(id: String): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun chooseModel(id: String): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun chooseReasoning(id: String): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun apply(): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun discardChanges(): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun retry(): Result<Unit>

    @Throwable(CancellationException::class)
    suspend fun create(prompt: String): Result<SessionBootstrapResult>
}
