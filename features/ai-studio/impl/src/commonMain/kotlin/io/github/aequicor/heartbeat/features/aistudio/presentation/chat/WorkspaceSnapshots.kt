package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.Throws as Throwable

internal class WorkspaceSnapshots(
    private val preferences: PreferencesStore,
    private val persistence: StudioWorkspaceStorage?,
    private val useMockWorkspace: Boolean,
) {

    private val pending = Channel<WriteRequest>(
        capacity = Channel.CONFLATED,
        onUndeliveredElement = { it.completion?.cancel() },
    )

    fun schedule(workspace: AuroraWorkspace) {
        pending.trySend(WriteRequest(workspace))
    }

    @Throwable(CancellationException::class)
    suspend fun saveNow(workspace: AuroraWorkspace): Result<Unit> {
        // The Store serializes snapshot requests and waits here before it can schedule another one.
        val completion = CompletableDeferred<Result<Unit>>()
        val request = pending.trySend(WriteRequest(workspace, completion))
        if (request.isFailure) {
            val cause = request.exceptionOrNull() ?: IllegalStateException("Workspace snapshot writer is unavailable")
            return Result.failure(StudioRepositoryException("Не удалось сохранить черновик", cause))
        }
        return completion.await()
    }

    fun close() {
        pending.close()
    }

    fun emptyWorkspace(): AuroraWorkspace =
        if (useMockWorkspace) AuroraMock.workspace() else AuroraWorkspace("", listOf())

    suspend fun restore(): AuroraWorkspace = persistence?.restore(::restorePreferences) ?: restorePreferences()

    suspend fun write(restored: AuroraWorkspace, onSaved: () -> Unit, onFailure: () -> Unit) {
        var saved = restored
        for (request in pending) {
            val result = runCatching { persist(request.snapshot, saved) }
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) {
                request.completion?.cancel(failure)
                currentCoroutineContext().ensureActive()
                continue
            }
            if (result.isSuccess) {
                saved = request.snapshot
                onSaved()
            } else {
                onFailure()
            }
            request.completion?.complete(result)
        }
    }

    private suspend fun restorePreferences(): AuroraWorkspace {
        val data = preferences.observeString(WorkspaceKey).first()
        if (data.isEmpty()) return emptyWorkspace()
        val restored = Json.decodeFromString<AuroraWorkspace>(data).recover()
        return if (useMockWorkspace) restored.withMockNavigation() else restored
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    private suspend fun persist(snapshot: AuroraWorkspace, previous: AuroraWorkspace) {
        if (persistence == null) preferences.putString(WorkspaceKey, Json.encodeToString(snapshot))
        else persistence.save(snapshot, previous.workStates(), previous.chats.associateBy { it.summary.id })
    }

    private class WriteRequest(
        val snapshot: AuroraWorkspace,
        val completion: CompletableDeferred<Result<Unit>>? = null,
    )

    private companion object {

        const val WorkspaceKey = "workspace_v1"
    }
}

private fun AuroraWorkspace.workStates(): Map<String, SessionWorkState> =
    chats.associate { it.summary.id to it.summary.workState }
