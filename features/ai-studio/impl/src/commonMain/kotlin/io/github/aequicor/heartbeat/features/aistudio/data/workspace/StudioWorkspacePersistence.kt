package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.dao.accessStudioDatabase
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.db.SelectWorkspaceSessions
import io.github.aequicor.heartbeat.features.aistudio.db.StudioProjectQueries
import io.github.aequicor.heartbeat.features.aistudio.db.StudioWorkspaceQueries
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.StudioWorkspaceStorage
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.toNavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.workState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
internal class StudioWorkspacePersistence(private val connection: DatabaseConnection) : StudioWorkspaceStorage {

    private val restoreLock = Mutex()
    private var recoveredWork = false
    private val queries get() = StudioWorkspaceQueries(connection.driver)

    override suspend fun restore(legacy: suspend () -> AuroraWorkspace): AuroraWorkspace = restoreLock.withLock {
        val stored = read(resetInterruptedWork = !recoveredWork) ?: run {
            import(legacy())
            checkNotNull(read())
        }
        recoveredWork = true
        stored
    }

    override fun save(workspace: AuroraWorkspace, previousWorkStates: Map<String, SessionWorkState>) {
        val metadata = Json.encodeToString(workspace.copy(chats = listOf()))
        accessStudioDatabase("Save studio workspace") {
            val workspaceQueries = queries
            workspaceQueries.transaction {
                for (chat in workspace.chats.filter(AuroraChat::payloadLoaded)) {
                    val id = chat.summary.id
                    workspaceQueries.updateWorkspaceSession(
                        payload = Json.encodeToString(chat),
                        summary = Json.encodeToString(chat.summary),
                        id = id,
                    )
                    val workState = chat.summary.workState
                    val previousState = previousWorkStates[id] ?: SessionWorkState.Idle
                    if (workState != previousState) {
                        workspaceQueries.updateWorkspaceSessionWorkState(workState.name, id, previousState.name)
                    }
                }
                workspaceQueries.upsertWorkspaceMetadata(metadata)
            }
        }
    }

    override fun load(chat: AuroraChat): AuroraChat {
        if (chat.payloadLoaded) return chat
        return try {
            val payload = accessStudioDatabase("Read studio session content") {
                queries.selectWorkspacePayload(chat.summary.id).executeAsOneOrNull()?.workspace_payload
            }
            val saved = payload?.let { Json.decodeFromString<AuroraChat>(it) } ?: chat
            saved.copy(summary = chat.summary, payloadLoaded = true).recover()
        } catch (failure: DaoException) {
            throw StudioRepositoryException("Cannot read session content", failure)
        } catch (failure: SerializationException) {
            throw StudioRepositoryException("Cannot decode session content", failure)
        }
    }

    private fun read(resetInterruptedWork: Boolean = false): AuroraWorkspace? =
        accessStudioDatabase("Read studio workspace") {
            val workspaceQueries = queries
            workspaceQueries.transactionWithResult {
                val metadata = workspaceQueries.selectWorkspaceMetadata().executeAsOneOrNull()
                    ?: return@transactionWithResult null
                if (resetInterruptedWork) workspaceQueries.resetInterruptedWorkStates()
                decode(metadata, workspaceQueries.selectWorkspaceSessions().executeAsList())
            }
        }

    private fun import(workspace: AuroraWorkspace) {
        val projectId = ProjectId.Ungrouped.value.toString()
        val sessionIds = workspace.chats.associate { it.summary.id to Uuid.random().toString() }
        val expandAllRoots = workspace.chats.any { it.summary.project in workspace.expandedProjects }
        val metadata = AuroraWorkspace(
            selectedId = sessionIds[workspace.selectedId].orEmpty(),
            chats = listOf(),
            serial = workspace.serial,
            expandedProjects = if (expandAllRoots) setOf(projectId) else setOf(),
        )
        accessStudioDatabase("Import studio workspace") {
            val workspaceQueries = queries
            workspaceQueries.transaction {
                if (workspaceQueries.selectWorkspaceMetadata().executeAsOneOrNull() != null) return@transaction
                if (workspace.chats.any { it.summary.project == Chat.UngroupedProjectKey }) {
                    val expanded = "project:${Chat.UngroupedProjectKey}" !in workspace.collapsedNavigation
                    StudioProjectQueries(connection.driver).updateProjectExpanded(if (expanded) 1L else 0L, projectId)
                }
                workspace.chats.forEachIndexed { index, chat ->
                    val old = chat.summary
                    val summary = old.copy(
                        id = sessionIds.getValue(old.id),
                        project = Chat.UngroupedProjectKey,
                        projectId = projectId,
                        parentId = sessionIds[old.parentId],
                    )
                    val archiveStatus = if (old.id in workspace.archivedChats) SessionStatus.ARCHIVED
                    else SessionStatus.ACTIVE
                    val workState = summary.workState.takeIf { it == SessionWorkState.Waiting }
                        ?: SessionWorkState.Idle
                    workspaceQueries.importWorkspaceSession(
                        id = summary.id,
                        project_id = projectId,
                        title = summary.title,
                        status = archiveStatus.name,
                        updated_at = -index.toLong(),
                        parent_id = summary.parentId,
                        expanded = if (old.id in workspace.collapsedNavigation) 0L else 1L,
                        payload = Json.encodeToString(chat.copy(summary = summary, mockId = old.id)),
                        summary = Json.encodeToString(summary),
                        work_state = workState.name,
                    )
                }
                workspaceQueries.upsertWorkspaceMetadata(Json.encodeToString(metadata))
            }
        }
    }

    private fun decode(metadata: String, sessions: List<SelectWorkspaceSessions>): AuroraWorkspace {
        val workspace = Json.decodeFromString<AuroraWorkspace>(metadata)
        val chats = sessions.map { record ->
            val summary = record.workspace_summary?.let { Json.decodeFromString<Chat>(it) }
                ?: Chat(record.id, record.title, record.alias)
            val restored = summary.copy(
                id = record.id,
                title = record.title,
                project = record.alias,
                projectId = record.project_id,
                parentId = record.parent_session_id,
                navigationStatus = SessionWorkState.valueOf(record.work_state).toNavigationStatus(),
            )
            AuroraChat(restored, payloadLoaded = false)
        }
        return workspace.copy(
            chats = chats,
            archivedChats = sessions.filter { it.status == SessionStatus.ARCHIVED.name }.map { it.id }.toSet(),
            collapsedNavigation = buildSet {
                for (record in sessions) {
                    if (record.is_expanded == 0L) add(record.id)
                    if (record.project_expanded == 0L) add("project:${record.project_id}")
                }
            },
        )
    }
}
