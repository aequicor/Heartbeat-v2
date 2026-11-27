package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import io.github.aequicor.heartbeat.features.aistudio.data.dto.decodeChatMessageBody
import app.cash.sqldelight.coroutines.asFlow

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatDraftOwnership
import io.github.aequicor.heartbeat.features.aistudio.data.dao.accessStudioDatabase
import io.github.aequicor.heartbeat.features.aistudio.data.dao.readFileCatalog
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.toDomain
import io.github.aequicor.heartbeat.features.aistudio.db.ChatDraftQueries
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.SelectWorkspaceSessions
import io.github.aequicor.heartbeat.features.aistudio.db.StudioProjectQueries
import io.github.aequicor.heartbeat.features.aistudio.db.StudioWorkspaceQueries
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SecurityMode
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSecurityMode
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ContextUsage
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.StudioWorkspaceMetadata
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.display
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.toNavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.workState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Choice
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Selection
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
internal class StudioWorkspacePersistence(
    private val connection: DatabaseConnection,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
    @Named("studio-chat-writes") private val writeLock: Mutex,
    private val draftOwnership: ChatDraftOwnership,
    private val attachmentMigration: AiStudioAttachmentMigration,
) : StudioWorkspaceMetadata {

    private val restoreLock = Mutex()
    private var recoveredWork = false

    private val queries get() = StudioWorkspaceQueries(connection.driver)

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun listFiles(sessionId: String): List<File> = withContext(ioDispatcher) {
        workspaceOperation("Read session file catalog") {
            ChatQueries(connection.driver).readFileCatalog(sessionId).map { record ->
                record.toDomain().display().copy(catalogUri = record.uri)
            }
        }
    }

    override fun observeSecurityMode(sessionId: String): Flow<SecurityMode?> = flow {
        val query = withContext(ioDispatcher) { ChatQueries(connection.driver).chatSecurityMode(sessionId) }
        emitAll(query.asFlow().map {
            withContext(ioDispatcher) {
                workspaceOperation("Read chat mode") {
                    query.executeAsOneOrNull()?.security_config_json?.let { json ->
                        Json.decodeFromString<ChatSecurityMode>(json).display()
                    }
                }
            }
        }.distinctUntilChanged())
    }

    override fun observeContextUsage(sessionId: String): Flow<ContextUsage?> = flow {
        val query = withContext(ioDispatcher) {
            workspaceOperation("Observe chat context usage") { ChatQueries(connection.driver).chatContextUsage(sessionId) }
        }
        emitAll(query.asFlow().map {
            withContext(ioDispatcher) {
                workspaceOperation("Read chat context usage") {
                    query.executeAsOneOrNull()?.let { row -> contextUsage(row.context_used_tokens, row.context_size_tokens) }
                }
            }
        }.distinctUntilChanged())
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun restore(legacy: suspend () -> AuroraWorkspace): AuroraWorkspace = withContext(ioDispatcher) {
        workspaceOperation("Restore studio workspace") {
            restoreLock.withLock {
                val stored = writeLock.withLock { read(resetInterruptedWork = !recoveredWork) } ?: run {
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
                val drafts = ChatDraftQueries(connection.driver)
                val writer by lazy(LazyThreadSafetyMode.NONE) { draftOwnership.claim()?.toString() }
                workspaceQueries.transaction {
                    // Take the SQLite write lock before reading draft revisions from another window.
                    workspaceQueries.upsertWorkspaceMetadata(metadata)
                    for (chat in workspace.chats.filter(AuroraChat::payloadLoaded)) {
                        val id = chat.summary.id
                        val workState = chat.summary.workState
                        val previousState = previousWorkStates[id] ?: SessionWorkState.Idle
                        val writerOwner = if (workState.isActive || previousState.isActive) writer else null
                        val saved = workspaceQueries.updateWorkspaceSession(
                            Json.encodeToString(chat.withoutDraft().copy(messages = emptyList(), interrupted = null)),
                            Json.encodeToString(chat.summary), id, writerOwner,
                        ).value
                        if (saved == 0L) continue
                        drafts.saveDraft(history, chat, previousChats[id])
                        if (!chat.persistedThroughDomain) {
                            val chatId = history.chatForSession(id).executeAsOneOrNull()
                            if (chatId != null) history.saveHistory(chatId, chat, previousChats[id])
                        }
                        if (workState != previousState) {
                            workspaceQueries.updateWorkspaceSessionWorkState(
                                work_state = workState.name,
                                writer_owner = writerOwner,
                                id = id,
                                previous_work_state = previousState.name,
                            )
                        }
                    }
                }
            }
        }
    }

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    override suspend fun loadMetadata(chat: AuroraChat): AuroraChat = withContext(ioDispatcher) {
        if (chat.payloadLoaded && !chat.summary.workState.isActive && chat.summary.configuration == null) return@withContext chat
        writeLock.withLock {
            workspaceOperation("Read studio session content") {
                val workspaceQueries = queries
                val sessionId = chat.summary.id
                val owner = workspaceQueries.selectActiveWorkOwner(sessionId).executeAsOneOrNull()
                if (owner != null && hasEnded(owner)) workspaceQueries.resetInterruptedWorkState(sessionId, owner)
                workspaceQueries.transactionWithResult { readChatMetadata(workspaceQueries, chat) }
            }
        }
    }

    private fun readChatMetadata(workspaceQueries: StudioWorkspaceQueries, chat: AuroraChat): AuroraChat {
        val sessionId = chat.summary.id
        val record = workspaceQueries.selectWorkspacePayload(sessionId).executeAsOne()
        val navigationStatus = SessionWorkState.valueOf(record.work_state).toNavigationStatus()
        val measured = if (record.engine_id == null) chat.summary
        else chat.summary.withUsage(contextUsage(record.context_used_tokens, record.context_size_tokens))
        if (chat.payloadLoaded) {
            if (navigationStatus == chat.summary.navigationStatus && measured == chat.summary) return chat
            return chat.copy(summary = measured.copy(navigationStatus = navigationStatus))
        }
        val saved = record.workspace_payload?.let { Json.decodeFromString<AuroraChat>(it) } ?: chat
        val labels = saved.summary.configuration
        val configuration = record.engine_id?.let { engine ->
            Selection(
                engine.asChoice(labels?.engine),
                record.model_name?.asChoice(labels?.model),
                record.reasoning_level?.asChoice(labels?.level),
            )
        }
        val summary = measured.copy(configuration = configuration, navigationStatus = navigationStatus)
        val drafts = ChatDraftQueries(connection.driver)
        val draft = drafts.selectDraft(sessionId).executeAsOneOrNull()
        return saved.copy(
            summary = summary, messages = emptyList(), interrupted = null, payloadLoaded = true,
            draft = draft?.body.orEmpty(),
            attachments = if (draft == null) emptyList() else drafts.readDraftFiles(sessionId),
            draftRevision = draft?.revision, loadedDraftRevision = draft?.revision,
            messageBody = decodeChatMessageBody(draft?.message_body),
        )
    }

    private fun read(resetInterruptedWork: Boolean = false): AuroraWorkspace? =
        accessStudioDatabase("Read studio workspace") {
            val workspaceQueries = queries
            val endedOwners = if (resetInterruptedWork) {
                workspaceQueries.selectActiveWorkOwners().executeAsList().filter(::hasEnded)
            } else {
                emptyList()
            }
            workspaceQueries.transactionWithResult {
                if (resetInterruptedWork) workspaceQueries.resetInterruptedWorkStates(endedOwners)
                val metadata = workspaceQueries.selectWorkspaceMetadata().executeAsOneOrNull()
                    ?: return@transactionWithResult null
                decode(metadata, workspaceQueries.selectWorkspaceSessions().executeAsList())
            }
        }

    private fun hasEnded(owner: String): Boolean = Uuid.parseOrNull(owner)?.let(draftOwnership::ended) == true

    private fun import(workspace: AuroraWorkspace) {
        val projectId = ProjectId.Ungrouped.value.toString()
        val sessionIds = workspace.chats.associate { it.summary.id to legacyWorkspaceSessionId(it.summary.id) }
        val expandAllRoots = workspace.chats.any { it.summary.project in workspace.expandedProjects }
        val metadata = AuroraWorkspace(
            selectedId = sessionIds[workspace.selectedId].orEmpty(),
            chats = listOf(),
            serial = workspace.serial,
            expandedProjects = if (expandAllRoots) setOf(projectId) else setOf(),
        )
        accessStudioDatabase("Import studio workspace") {
            val workspaceQueries = queries
            val drafts = ChatDraftQueries(connection.driver)
            val history = ChatQueries(connection.driver)
            workspaceQueries.transaction {
                if (workspaceQueries.initializeWorkspaceMetadata(Json.encodeToString(metadata)).value == 0L) return@transaction
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
                    val imported = chat.copy(summary = summary, mockId = old.id).recover()
                    workspaceQueries.importWorkspaceSession(
                        id = summary.id,
                        project_id = projectId,
                        title = summary.title,
                        status = archiveStatus.name,
                        updated_at = -index.toLong(),
                        parent_id = summary.parentId,
                        expanded = if (old.id in workspace.collapsedNavigation) 0L else 1L,
                        payload = Json.encodeToString(imported.withoutDraft().copy(messages = emptyList(), interrupted = null)),
                        summary = Json.encodeToString(summary),
                        work_state = workState.name,
                    )
                    if (chat.draft.isNotEmpty() || chat.attachments.isNotEmpty()) {
                        drafts.replaceDraft(
                            history, summary.id, Uuid.random().toString(), chat.draft,
                            attachmentMigration.copyLegacyDraftFiles(projectId, summary.id, chat.attachments),
                        )
                    }
                    if (imported.messages.isNotEmpty()) {
                        val archived = attachmentMigration.archiveLegacyHistory(projectId, summary.id, imported)
                        history.saveHistory(history.chatForSession(summary.id).executeAsOne(), archived, previous = null)
                    }
                }
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
            val measured = if (record.engine_id == null) restored
            else restored.withUsage(contextUsage(record.context_used_tokens, record.context_size_tokens))
            AuroraChat(measured, payloadLoaded = false)
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

private fun contextUsage(used: Long?, size: Long?): ContextUsage? =
    if (used != null && used >= 0 && size != null && size > 0) ContextUsage(used, size) else null

private fun ChatDraftQueries.saveDraft(history: ChatQueries, chat: AuroraChat, previous: AuroraChat?) {
    val revision = chat.draftRevision ?: return
    val id = chat.summary.id
    val previousRevision = if (previous?.payloadLoaded == true) previous.draftRevision else chat.loadedDraftRevision
    // Rewriting metadata must not resurrect a submitted draft from a stale window.
    if (revision == previousRevision) return
    val storedRevision = selectDraft(id).executeAsOneOrNull()?.revision
    if (revision == storedRevision) return
    check(storedRevision == previousRevision) { "Черновик изменился в другом окне" }
    replaceDraft(history, id, revision, chat.draft, chat.attachments, chat.messageBody)
}

private fun AuroraChat.withoutDraft() = copy(draft = "", attachments = emptyList(), messageBody = null)

private fun legacyWorkspaceSessionId(id: String): String {
    // Keep the target directory stable if an import stops after writing its files.
    val bytes = "workspace_v1:$id".encodeUtf8().sha256().substring(0, 16).toByteArray()
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x80).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    return Uuid.fromByteArray(bytes).toString()
}

@Suppress("TooGenericExceptionCaught") // Driver and legacy serializer exceptions cross the same storage boundary.
private inline fun <T> workspaceOperation(operation: String, block: () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    throw StudioRepositoryException(operation, failure)
}
