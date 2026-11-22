package io.github.aequicor.heartbeat.features.aistudio.presentation.state

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.AuroraMock
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.withMockNavigation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

internal class WorkspaceSnapshots(
    private val preferences: PreferencesStore,
    private val persistence: StudioWorkspaceStorage?,
    private val useMockWorkspace: Boolean,
) {
    private val pending = Channel<AuroraWorkspace>(Channel.CONFLATED)

    fun schedule(workspace: AuroraWorkspace) {
        pending.trySend(workspace)
    }

    fun close() {
        pending.close()
    }

    fun emptyWorkspace(): AuroraWorkspace =
        if (useMockWorkspace) AuroraMock.workspace() else AuroraWorkspace("", listOf())

    suspend fun restore(): AuroraWorkspace = persistence?.restore(::restorePreferences) ?: restorePreferences()

    suspend fun write(restored: AuroraWorkspace, onSaved: () -> Unit, onFailure: () -> Unit) {
        var savedWorkStates = restored.workStates()
        for (snapshot in pending) {
            if (persist(snapshot, savedWorkStates)) {
                savedWorkStates = snapshot.workStates()
                onSaved()
            } else onFailure()
        }
    }

    private suspend fun restorePreferences(): AuroraWorkspace {
        val data = preferences.observeString(WorkspaceKey).first()
        if (data.isEmpty()) return emptyWorkspace()
        val restored = Json.decodeFromString<AuroraWorkspace>(data).recover()
        return if (useMockWorkspace) restored.withMockNavigation() else restored
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun persist(
        snapshot: AuroraWorkspace,
        previousWorkStates: Map<String, SessionWorkState>,
    ): Boolean = try {
        if (persistence == null) preferences.putString(WorkspaceKey, Json.encodeToString(snapshot))
        else persistence.save(snapshot, previousWorkStates)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val WorkspaceKey = "workspace_v1"
    }
}

private fun AuroraWorkspace.workStates(): Map<String, SessionWorkState> =
    chats.associate { it.summary.id to it.summary.workState }
