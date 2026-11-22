package io.github.aequicor.heartbeat.features.aistudio.data.dao

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
) : ChatHistoryDao {

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun page(sessionId: String, before: Long?, limit: Int): List<ChatEntryRecord> = read {
        val chatId = chatForSession(sessionId).executeAsOne()
        transactionWithResult {
            selectHistoryPage(chatId, before, limit.toLong()).executeAsList().map { row ->
                val deferred = historyUsesPreview(row.status)
                val query = if (deferred) selectPreviewParts(row.revision_id, ::Message_part) else selectParts(row.revision_id)
                val parts = query.executeAsList().map { part ->
                    val attachment = part.attachment_id?.let { id ->
                        if (deferred) attachmentHeader(id) { key, size, json ->
                            ChatEntryRecord.Attachment(key, size, json, null)
                        }.executeAsOne() else fullAttachment(id)
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
    override suspend fun position(sessionId: String, messageId: String): Long? = read {
        selectMessage(chatForSession(sessionId).executeAsOne(), messageId).executeAsOneOrNull()?.position
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun files(sessionId: String): List<ChatEntryRecord.Attachment> = read {
        chatFileHeaders(chatForSession(sessionId).executeAsOne()) { id, size, header ->
            ChatEntryRecord.Attachment(id, size, header, null)
        }.executeAsList()
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun file(id: String): ChatEntryRecord.Attachment = read { fullAttachment(id) }

    private fun ChatQueries.fullAttachment(id: String): ChatEntryRecord.Attachment = selectAttachment(id).executeAsOne().let {
        ChatEntryRecord.Attachment(it.id, checkNotNull(it.size_bytes), it.presentation, checkNotNull(it.inline_text))
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun partBody(id: Long): String = read { selectPartBody(id).executeAsOne().body.orEmpty() }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun revision(chatId: String, revisionId: Long): ChatEntryRecord.Revision = read {
        revisionForEdit(chatId, revisionId, ChatEntryRecord::Revision).executeAsOne()
    }

    private suspend fun <T> read(query: ChatQueries.() -> T): T = withContext(ioDispatcher) {
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
                            attachment_id = null, presentation = null,
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

internal fun ChatQueries.applyEdit(chatId: String, position: Long, revision: Long, keepContinuation: Boolean) {
    selectRevision(revision, chatId, position)
    if (keepContinuation) markLaterReplies(chatId, position) else truncateAfter(chatId, position)
    markContextStale(chatId)
}

internal fun ChatQueries.clearUnreferencedHistory() {
    deleteUnreferencedRevisions()
    deleteUnreferencedAttachments()
}
