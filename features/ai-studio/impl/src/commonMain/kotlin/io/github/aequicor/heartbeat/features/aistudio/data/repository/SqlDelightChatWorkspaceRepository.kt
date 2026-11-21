package io.github.aequicor.heartbeat.features.aistudio.data.repository

import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatWorkspace
import io.github.aequicor.heartbeat.features.aistudio.domain.model.WorkspaceChat
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ChatWorkspaceRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The workspace row commits the ordered set of chat snapshots and marks a completed import. */
internal class SqlDelightChatWorkspaceRepository<C : WorkspaceChat>(
    private val connection: DatabaseConnection,
    private val legacyPreferences: PreferencesStore,
    private val chatSerializer: KSerializer<C>,
) : ChatWorkspaceRepository<C> {
    override suspend fun load(): ChatWorkspace<C>? {
        val queries = ChatQueries(connection.driver)
        return queries.transactionWithResult { read(queries) } ?: importLegacy(queries)
    }

    private suspend fun importLegacy(queries: ChatQueries): ChatWorkspace<C>? {
        val legacy = legacyPreferences.observeString(WorkspaceKey).first()
        return queries.transactionWithResult {
            read(queries) ?: legacy.takeIf(String::isNotEmpty)
                ?.let { Json.decodeFromString(ChatWorkspace.serializer(chatSerializer), it) }
                ?.also { write(queries, it) }
        }
    }

    override suspend fun save(workspace: ChatWorkspace<C>) {
        val queries = ChatQueries(connection.driver)
        queries.transaction { write(queries, workspace) }
    }

    private fun read(queries: ChatQueries): ChatWorkspace<C>? {
        val snapshot = queries.selectWorkspace().executeAsOneOrNull() ?: return null
        val metadata = Json.decodeFromString<WorkspaceMetadata>(snapshot)
        check(metadata.chatIds.size == metadata.chatIds.toSet().size) { "Duplicate IDs in chat workspace" }
        val rows = queries.selectSnapshots { id, payload -> id to payload }.executeAsList().toMap()
        val chats = metadata.chatIds.map { id ->
            val payload = checkNotNull(rows[id]) { "Missing chat snapshot for $id" }
            Json.decodeFromString(chatSerializer, payload).also { check(it.id == id) }
        }
        return ChatWorkspace(
            metadata.selectedId, chats, metadata.serial, metadata.collapsedNavigation,
            metadata.expandedProjects, metadata.archivedChats,
        )
    }

    private fun write(queries: ChatQueries, workspace: ChatWorkspace<C>) {
        val ids = workspace.chats.map(WorkspaceChat::id)
        require(ids.size == ids.toSet().size) { "Duplicate IDs in chat workspace" }
        val previous = queries.selectSnapshots { id, payload -> id to payload }.executeAsList().toMap()
        for (chat in workspace.chats) {
            val payload = Json.encodeToString(chatSerializer, chat)
            if (payload != previous[chat.id]) queries.upsertSnapshot(chat.id, chat.title, payload)
        }
        for (id in previous.keys - ids.toSet()) queries.deleteById(id)
        queries.upsertWorkspace(Json.encodeToString(WorkspaceMetadata(
            workspace.selectedId, ids, workspace.serial, workspace.collapsedNavigation,
            workspace.expandedProjects, workspace.archivedChats,
        )))
    }

    @Serializable
    private data class WorkspaceMetadata(
        val selectedId: String,
        val chatIds: List<String>,
        val serial: Long,
        val collapsedNavigation: Set<String>,
        val expandedProjects: Set<String>,
        val archivedChats: Set<String>,
    )

    private companion object {
        const val WorkspaceKey = "workspace_v1"
    }
}
