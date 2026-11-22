package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.dao.accessStudioDatabase
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.SelectWorkspaceSessions
import io.github.aequicor.heartbeat.features.aistudio.db.StudioProjectQueries
import io.github.aequicor.heartbeat.features.aistudio.db.StudioWorkspaceQueries
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.StudioWorkspaceMetadata
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.toNavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.workState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Choice
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Selection
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
internal class StudioWorkspacePersistence(
    private val connection: DatabaseConnection,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
    @Named("studio-chat-writes") private val writeLock: Mutex = Mutex(),
) : StudioWorkspaceMetadata {

    private val restoreLock = Mutex()
    private var recoveredWork = false

    private val queries get() = StudioWorkspaceQueries(connection.driver)

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun restore(legacy: suspend () -> AuroraWorkspace): AuroraWorkspace = withContext(ioDispatcher) {
        workspaceOperation("Restore studio workspace") {
            restoreLock.withLock {
                val stored = read(resetInterruptedWork = !recoveredWork) ?: run {
                    import(legacy())
                    checkNotNull(read())
                }
                recoveredWork = true
                stored
            }
        }
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun save(
        workspace: AuroraWorkspace,
        previousWorkStates: Map<String, SessionWorkState>,
        previousChats: Map<String, AuroraChat>,
    ): Unit = withContext(ioDispatcher) {
        val metadata = Json.encodeToString(workspace.copy(chats = listOf()))
        writeLock.withLock {
            workspaceOperation("Save studio workspace") {
                val workspaceQueries = queries
                val history = ChatQueries(connection.driver)
                workspaceQueries.transaction {
                    for (chat in workspace.chats.filter(AuroraChat::payloadLoaded)) {
                        val id = chat.summary.id
                        val chatId = history.chatForSession(id).executeAsOneOrNull() ?: continue
                        if (!chat.persistedThroughDomain) history.saveHistory(chatId, chat, previousChats[id])
                        workspaceQueries.updateWorkspaceSession(
                            payload = Json.encodeToString(chat.copy(messages = listOf(), interrupted = null)),
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
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun loadMetadata(chat: AuroraChat): AuroraChat {
        if (chat.payloadLoaded) return chat
        return readStored("Read studio session content") { history ->
            val sessionId = chat.summary.id
            val record = queries.selectWorkspacePayload(sessionId).executeAsOne()
            val saved = record.workspace_payload?.let { Json.decodeFromString<AuroraChat>(it) } ?: chat
            val labels = saved.summary.configuration
            val configuration = record.engine_id?.let { engine ->
                Selection(
                    engine.asChoice(labels?.engine),
                    record.model_name?.asChoice(labels?.model),
                    record.reasoning_level?.asChoice(labels?.level),
                )
            }
            val summary = chat.summary.copy(configuration = configuration)
            val chatId = history.chatForSession(sessionId).executeAsOne()
            if (saved.messages.isNotEmpty() || saved.interrupted != null) {
                history.saveHistory(chatId, saved, previous = null)
                val metadata = saved.copy(messages = listOf(), interrupted = null)
                queries.updateWorkspaceSession(
                    Json.encodeToString(metadata), Json.encodeToString(chat.summary), sessionId,
                )
            }
            saved.copy(summary = summary, messages = emptyList(), interrupted = null, payloadLoaded = true)
        }
    }

    private suspend fun <T> readStored(operation: String, block: (ChatQueries) -> T): T = withContext(ioDispatcher) {
        writeLock.withLock {
            workspaceOperation(operation) {
                val history = ChatQueries(connection.driver)
                history.transactionWithResult { block(history) }
            }
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

private fun String.asChoice(previous: Choice?): Choice = previous?.takeIf { it.id == this } ?: Choice(this, this)

@Suppress("TooGenericExceptionCaught") // Driver and legacy serializer exceptions cross the same storage boundary.
private inline fun <T> workspaceOperation(operation: String, block: () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    throw StudioRepositoryException(operation, failure)
}
