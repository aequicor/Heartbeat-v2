package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.isSubagentToolName
import io.github.aequicor.heartbeat.features.aistudio.subagentNameFromOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.StudioSessionQueries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.SentAttachmentRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatInterruptionLabels
import io.github.aequicor.heartbeat.features.aistudio.db.Message_part
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Inject
internal class ChatHistoryDaoImpl(
    private val connection: DatabaseConnection,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
    @Named("studio-chat-writes") private val writeLock: Mutex,
    private val draftOwnership: ChatDraftOwnership,
) : ChatHistoryDao {

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun page(
        sessionId: String, before: Long?, limit: Int, interruption: ChatInterruptionLabels,
    ): List<ChatEntryRecord> = read {
        val chatId = chatForSession(sessionId).executeAsOne()
        if (before == null) writeLock.withLock { recoverInterruptedDrafts(chatId, draftOwnership, interruption) }
        transactionWithResult {
            selectHistoryPage(chatId, before, limit.toLong()).executeAsList().map { row ->
                val deferred = historyUsesPreview(row.status)
                val parts = historyParts(row.revision_id, deferred).map { part ->
                    val attachment = part.sent_attachment_id?.let { id ->
                        if (deferred) fileHeader(id) else fullAttachment(id)
                    }
                    ChatEntryRecord.Part(part.id, part.parent_part_id, part.kind, part.title, part.body,
                        part.presentation, attachment, deferred && part.kind in setOf("reasoning", "tool_call"))
                }
                ChatEntryRecord(row.position, row.revision_id, row.message_id, row.predates_edit != 0L,
                    row.role, row.presentation, parts)
            }
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun source(sessionId: String, displayId: String?, revisionId: Long?): ChatEntryRecord? = read {
        transactionWithResult {
            val chatId = chatForSession(sessionId).executeAsOne()
            val row = selectReferenceSource(chatId, displayId, revisionId).executeAsOneOrNull()
                ?: return@transactionWithResult null
            val parts = selectParts(row.revision_id).executeAsList().map { part ->
                val snapshot = part.sent_attachment_id
                ChatEntryRecord.Part(
                    part.id, part.parent_part_id, part.kind, part.title, part.body, part.presentation,
                    snapshot?.let { fullAttachment(it) }, deferred = false,
                    snapshot = if (row.role == "user") snapshot?.let { id ->
                        selectSentAttachment(id).executeAsOne().let {
                            SentAttachmentRecord(it.transfer_mode, it.uri, it.name, it.description, it.mime_type,
                                it.size_bytes, it.content_text, it.content_blob)
                        }
                    } else null,
                )
            }
            ChatEntryRecord(row.position, row.revision_id, row.message_id, row.predates_edit != 0L,
                row.role, row.presentation, parts)
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun position(sessionId: String, messageId: String): Long? = read {
        selectMessage(chatForSession(sessionId).executeAsOne(), messageId).executeAsOneOrNull()?.position
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun files(sessionId: String): List<ChatEntryRecord.Attachment> = read { readFileCatalog(sessionId) }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun catalogAttachment(
        sessionId: String,
        snapshotId: String,
    ): Pair<ChatEntryRecord.Attachment, SentAttachmentRecord>? = read {
        transactionWithResult {
            if (!catalogContainsAttachment(sessionId, snapshotId).executeAsOne()) return@transactionWithResult null
            fileHeader(snapshotId) to selectSentAttachment(snapshotId).executeAsOne().toRecord()
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun file(id: String): ChatEntryRecord.Attachment = read { fullAttachment(id) }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun removeUnavailableAttachment(sessionId: String, snapshotId: String): Unit = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Remove attachment from catalog") {
                val history = ChatQueries(connection.driver)
                val chatId = history.chatForSession(sessionId).executeAsOne()
                history.transaction {
                    val projectId = history.catalogProjectForChat(chatId).executeAsOneOrNull()
                    if (projectId == null) history.removeCatalogAttachment(chatId, snapshotId)
                    else history.removeProjectCatalogAttachment(projectId, snapshotId)
                    history.clearUnreferencedHistory()
                }
            }
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun partBody(id: Long): String = read { selectPartBody(id).executeAsOne().body.orEmpty() }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun revision(chatId: String, revisionId: Long): ChatEntryRecord.Revision = read {
        revisionForEdit(chatId, revisionId, ChatEntryRecord::Revision).executeAsOne()
    }

    private suspend fun <T> read(query: suspend ChatQueries.() -> T): T = withContext(ioDispatcher) {
        accessStudioDatabase("Read chat history") { ChatQueries(connection.driver).query() }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun fork(chatId: String, revisionId: Long, title: String): String = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Fork chat") {
                val history = ChatQueries(connection.driver)
                history.transactionWithResult {
                    val point = history.committedEntry(chatId, revisionId).executeAsOne()
                    val id = Uuid.random().toString()
                    val now = Clock.System.now()
                    StudioSessionQueries(connection.driver).insertSession(
                        id, point.project_id, title, now.epochSeconds, now.nanosecondsOfSecond.toLong(), parent_id = null,
                    )
                    history.inheritChat(chatId, revisionId, id)
                    history.copyEntriesThrough(id, chatId, point.position)
                    id
                }
            }
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun editText(
        chatId: String, revisionId: Long, text: String, keepContinuation: Boolean,
    ): Unit = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Edit chat message") {
                val history = ChatQueries(connection.driver)
                history.transaction {
                    val point = history.committedEntry(chatId, revisionId).executeAsOne()
                    history.insertEditedRevision(
                        point.status, Clock.System.now().toEpochMilliseconds(), point.presentation, revisionId,
                    )
                    val revision = history.lastInsertedId().executeAsOne()
                    if (text.isNotBlank()) {
                        history.insertPart(
                            revision_id = revision, parent_part_id = null, position = 0, kind = "text",
                            body = text, title = null, status = null, tool_call_id = null,
                            presentation = null, sent_attachment_id = null,
                        )
                    }
                    history.copyUserAttachments(revision, revisionId, if (text.isBlank()) 0L else 1L)
                    history.applyEdit(chatId, point.position, revision, keepContinuation)
                    if (!keepContinuation) history.clearUnreferencedHistory()
                }
            }
        }
    }
}

internal fun historyUsesPreview(status: String): Boolean = status != "draft"

internal fun ChatQueries.historyParts(revision: Long, deferred: Boolean): List<Message_part> =
    if (deferred) {
        selectPreviewParts((ReasoningPreviewLength + 1).toString(), revision, ::Message_part)
            .executeAsList().map { part -> withLegacySubagentName(part) }
    } else {
        selectParts(revision).executeAsList()
    }

private fun ChatQueries.withLegacySubagentName(part: Message_part): Message_part {
    if (part.kind != "tool_call" || part.title?.isSubagentToolName() != true) return part
    val header = part.presentation?.let { Json.parseToJsonElement(it).jsonObject } ?: return part
    if (header["detailLabel"] is JsonPrimitive) return part
    val name = selectPartBody(part.id).executeAsOneOrNull()?.body?.subagentNameFromOutput() ?: return part
    return part.copy(presentation = JsonObject(header + ("detailLabel" to JsonPrimitive(name))).toString())
}

internal fun String.reasoningPreview(): String =
    if (length <= ReasoningPreviewLength) this else take(ReasoningPreviewLength - 1).trimEnd { it.isHighSurrogate() } + "…"

private const val ReasoningPreviewLength = 256

internal fun ChatQueries.applyEdit(chatId: String, position: Long, revision: Long, keepContinuation: Boolean) {
    selectRevision(revision, chatId, position)
    if (keepContinuation) markLaterReplies(chatId, position) else truncateAfter(chatId, position)
    markContextStale(chatId)
}

internal fun ChatQueries.clearUnreferencedHistory() {
    deleteUnreferencedRevisions()
    deleteUnreferencedSentAttachments()
    deleteUnreferencedAttachments()
}
