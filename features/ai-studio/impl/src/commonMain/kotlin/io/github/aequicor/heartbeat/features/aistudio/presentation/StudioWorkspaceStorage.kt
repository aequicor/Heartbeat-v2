package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.data.StudioCatalog
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.AuroraMock
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.withMockNavigation
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class StudioWorkspaceStorage(
    private val preferences: PreferencesStore,
    private val catalog: StudioCatalog?,
) {
    private var persisted = mapOf<String, AuroraChat>()

    suspend fun restore(onDamagedLegacy: () -> Unit): AuroraWorkspace {
        if (catalog == null) {
            val stored = preferences.observeString(WorkspaceKey).first()
            return if (stored.isEmpty()) AuroraMock.workspace()
            else Json.decodeFromString<AuroraWorkspace>(stored).recover().withMockNavigation()
        }
        val navigation = preferences.observeString(NavigationKey).first()
        val restored = if (navigation.isEmpty()) importLegacy(catalog, onDamagedLegacy)
        else decode(navigation) ?: AuroraWorkspace("", listOf())
        return catalog.load(restored)
    }

    private suspend fun importLegacy(catalog: StudioCatalog, onDamagedLegacy: () -> Unit): AuroraWorkspace {
        val stored = preferences.observeString(WorkspaceKey).first()
        val previous = if (stored.isEmpty()) null else decode(stored)?.recover()
        if (previous == null) {
            if (stored.isNotEmpty()) onDamagedLegacy()
            return AuroraWorkspace("", listOf())
        }
        catalog.importWorkspace(previous)
        val navigation = previous.navigationSettings()
        preferences.putString(NavigationKey, Json.encodeToString(navigation))
        preferences.remove(WorkspaceKey)
        return navigation
    }

    suspend fun save(snapshot: AuroraWorkspace) {
        if (catalog == null) {
            preferences.putString(WorkspaceKey, Json.encodeToString(snapshot))
        } else {
            val changed = snapshot.chats.filter { it != persisted[it.summary.id] }
            catalog.transaction {
                changed.forEach { catalog.saveDetails(it.summary.id, Json.encodeToString(it)) }
            }
            preferences.putString(NavigationKey, Json.encodeToString(snapshot.navigationSettings()))
            persisted = snapshot.chats.associateBy { it.summary.id }
        }
    }

    private fun decode(value: String): AuroraWorkspace? = try {
        Json.decodeFromString<AuroraWorkspace>(value)
    } catch (_: SerializationException) {
        null
    }

    private fun AuroraWorkspace.navigationSettings() =
        copy(chats = listOf(), projects = mapOf(), archivedChats = setOf())

    private companion object {
        const val WorkspaceKey = "workspace_v1"
        const val NavigationKey = "navigation_v2"
    }
}
