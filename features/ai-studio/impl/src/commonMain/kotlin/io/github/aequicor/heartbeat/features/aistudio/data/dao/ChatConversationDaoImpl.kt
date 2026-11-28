package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.dto.ChatMessageBodyPart
import io.github.aequicor.heartbeat.features.aistudio.data.dto.decodeChatMessageBody
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aiengine.api.session.ContextWindowUsage
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConfigRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConversationRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatEntryRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.SentAttachmentRecord
import io.github.aequicor.heartbeat.features.aistudio.db.ChatDraftQueries
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Message_part
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSecurityMode
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Inject
internal class ChatConversationDaoImpl(
    private val connection: DatabaseConnection,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
    @Named("studio-chat-writes") private val writeLock: Mutex,
    private val draftOwnership: ChatDraftOwnership,
) : ChatConversationDao {

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun find(sessionId: String): ChatConversationRecord? = withContext(ioDispatcher) {
        accessStudioDatabase("Read chat conversation") {
            ChatQueries(connection.driver).chatConversation(sessionId, ::ChatConversationRecord).executeAsOneOrNull()
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun updateSecurityMode(
        chatId: String, engineId: String, engineSessionId: String?, transform: (ChatSecurityMode?) -> ChatSecurityMode?,
    ): ChatSecurityMode? = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Update chat mode") {
                val queries = ChatQueries(connection.driver)
                queries.transactionWithResult {
                    val chat = queries.chatConversation(chatId, ::ChatConversationRecord).executeAsOne()
                    check(chat.engineId == engineId && chat.sessionId == engineSessionId) { "Сессия движка изменилась" }
                    val previous = chat.securityModeJson?.let { Json.decodeFromString<ChatSecurityMode>(it) }
                    val next = transform(previous)
                    if (next != previous) {
                        val snapshot = next?.let { Json.encodeToString(it) }
                        check(queries.setChatSecurityMode(snapshot, chatId, engineId, engineSessionId, chat.securityModeJson).value == 1L)
                        val preferred = next?.selectedValue
                        if (next != null && !next.requiresUserChoice && preferred != null) {
                            val preference = ChatSecurityMode.Preference(next.configId, next.options, preferred)
                            queries.rememberSecurityModePreference(Json.encodeToString(preference), engineId)
                        }
                    }
                    next
                }
            }
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun saveContextUsage(chatSessionId: String, engineSession: SessionKey, usage: ContextWindowUsage): Unit =
        withContext(ioDispatcher) {
            writeLock.withLock {
                accessStudioDatabase<Unit>("Save chat context usage") {
                    ChatQueries(connection.driver).saveContextUsage(
                        used = usage.used, size = usage.size, session_id = chatSessionId,
                        engine_id = engineSession.engine.id, engine_session_id = engineSession.id,
                    )
                }
            }
        }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun updateConfiguration(
        sessionId: String,
        expected: ChatConversationRecord,
        configuration: ChatConfigRecord,
    ): Boolean = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Apply chat configuration") {
                ChatQueries(connection.driver).updateChatConfiguration(
                    engine_id = configuration.engineId,
                    model_name = configuration.model,
                    reasoning_level = configuration.reasoning,
                    session_id = sessionId,
                    expected_engine_id = expected.engineId,
                    expected_model_name = expected.model,
                    expected_reasoning_level = expected.reasoning,
                    expected_engine_session_id = expected.sessionId,
                    expected_context_stale = expected.stale,
                ).value == 1L
            }
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun save(sessionId: String, messages: List<ChatTurnMessageRecord>): Unit = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Save chat turn") {
                val history = ChatQueries(connection.driver)
                val chat = history.chatForSession(sessionId).executeAsOne()
                val owner = if (messages.any { it.status == "draft" }) draftOwnership.claim() else null
                history.transaction {
                    for (message in messages) history.writeTurnMessage(chat, message, owner)
                }
            }
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun commitUserPrompt(
        sessionId: String,
        message: ChatTurnMessageRecord,
        expectedDraftRevision: String,
    ): ChatEntryRecord? = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Commit user prompt") {
                check(message.role == "user" && message.status == "draft" && message.expectedRevisionId == null)
                val history = ChatQueries(connection.driver)
                val chat = history.chatForSession(sessionId).executeAsOne()
                val owner = draftOwnership.claim()
                history.transactionWithResult {
                    val claimed = history.claimPromptSubmission(chat, message.id).value
                    if (claimed == 0L || history.selectMessage(chat, message.id).executeAsOneOrNull() != null) {
                        return@transactionWithResult null
                    }
                    val draftFiles = ChatDraftQueries(connection.driver).consumeDraft(
                        history, sessionId, expectedDraftRevision, owner?.toString(), message,
                    )
                    val revision = history.writeTurnMessage(chat, message, owner)
                    if (draftFiles.isNotEmpty()) history.deleteUnusedDraftFiles(draftFiles)
                    val saved = history.selectMessage(chat, message.id).executeAsOne()
                    history.readDelivery(revision, saved.position, saved.message_id, saved.presentation)
                }
            }
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun reserveRetry(
        sessionId: String,
        messageId: String,
        expectedFailedRevisionId: Long,
    ): ChatEntryRecord? = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Reserve prompt retry") {
                val history = ChatQueries(connection.driver)
                val chat = history.chatForSession(sessionId).executeAsOne()
                val failed = history.failedUserMessage(chat, messageId, expectedFailedRevisionId).executeAsOneOrNull()
                    ?: return@accessStudioDatabase null
                val meta = Json.parseToJsonElement(failed.presentation).jsonObject
                if (meta["retryAllowed"] != JsonPrimitive(true)) return@accessStudioDatabase null
                val sending = JsonObject(meta - "error" + mapOf(
                    "delivery" to JsonPrimitive("Sending"), "retryAllowed" to JsonPrimitive(false),
                )).toString()
                val header = draftOwnership.claim()?.let(sending::withDraftOwner) ?: sending
                history.transactionWithResult {
                    val inserted = history.insertRetryDraft(
                        Clock.System.now().toEpochMilliseconds(), header, chat, failed.position, failed.revision_id,
                        failed.presentation,
                    ).value
                    if (inserted != 1L) return@transactionWithResult null
                    val revision = history.lastInsertedId().executeAsOne()
                    history.copyPromptParts(revision, failed.revision_id)
                    check(history.selectRetryRevision(revision, chat, failed.position, failed.revision_id).value == 1L)
                    history.readDelivery(revision, failed.position, failed.message_id, header)
                }
            }
        }
    }
}

private fun ChatQueries.readDelivery(revision: Long, position: Long, messageId: Long, header: String): ChatEntryRecord {
    val parts = selectParts(revision).executeAsList().map { part ->
        val snapshot = part.sent_attachment_id?.let { snapshotId ->
            selectSentAttachment(snapshotId).executeAsOne().let {
                SentAttachmentRecord(
                    it.transfer_mode, it.uri, it.name, it.description, it.mime_type,
                    it.size_bytes, it.content_text, it.content_blob,
                )
            }
        }
        check(part.kind != "attachment" || snapshot != null) { "Missing attachment snapshot at part ${part.id}" }
        ChatEntryRecord.Part(
            part.id, part.parent_part_id, part.kind, part.title, part.body, part.presentation,
            part.sent_attachment_id?.let { fileHeader(it) }, deferred = false, snapshot = snapshot,
        )
    }
    return ChatEntryRecord(position, revision, messageId, false, "user", header, parts)
}

private fun ChatDraftQueries.consumeDraft(
    history: ChatQueries,
    sessionId: String,
    revision: String,
    writer: String?,
    message: ChatTurnMessageRecord,
): List<String> {
    val stored = selectDraft(sessionId).executeAsOneOrNull()
    check(stored?.revision == revision) { "Черновик изменился до отправки" }
    val files = selectDraftFiles(sessionId).executeAsList()
    val messageBody = message.parts.firstOrNull { it.kind == "text" && it.title == ChatMessageBodyPart }?.body
    val storedBody = decodeChatMessageBody(stored.message_body)
    check(stored.body.isNotBlank() || storedBody?.hasQuotes == true || files.isNotEmpty()) { "Черновик уже отправлен" }
    val prompt = message.parts.firstOrNull { it.kind == "text" && it.title == null }?.body.orEmpty()
    val submittedFiles = message.parts.mapNotNull { it.attachment }
    val submittedFileIds = submittedFiles.map { Json.parseToJsonElement(it.header).jsonObject.getValue("id").jsonPrimitive.content }
    check(
        stored.body.trim() == prompt &&
            storedBody == decodeChatMessageBody(messageBody) &&
            files.map { it.file_id } == submittedFileIds &&
            files.map { it.snapshot_id } == submittedFiles.map { it.existingId },
    ) {
        "Сообщение отличается от сохранённого черновика"
    }
    check(files.mapNotNull { it.snapshot_id }.all { history.catalogContainsAttachment(sessionId, it).executeAsOne() }) {
        "Вложение недоступно в каталоге"
    }
    check(clearSubmittedDraft(sessionId, revision, writer).value == 1L)
    deleteDraftFiles(sessionId)
    return files.map { it.attachment_id }
}

private fun ChatQueries.writeTurnMessage(chatId: String, message: ChatTurnMessageRecord, owner: Uuid?): Long {
    val header = if (message.status == "draft" && owner != null) message.header.withDraftOwner(owner) else message.header
    // A write as the first statement lets SQLite wait for another writer before taking a read snapshot.
    insertFirstRevision(message.role, message.status, Clock.System.now().toEpochMilliseconds(), message.id, header, chatId)
    val previous = selectMessage(chatId, message.id).executeAsOneOrNull()
    check(message.expectedRevisionId == null || previous?.revision_id == message.expectedRevisionId) {
        "The prompt revision is no longer selected"
    }
    val revision = if (previous == null) {
        lastInsertedId().executeAsOne().also { appendEntry(chatId, it) }
    } else if (message.replacesBackgroundReply && previous.status != "draft") {
        insertEditedRevision(message.status, Clock.System.now().toEpochMilliseconds(), header, previous.revision_id)
        lastInsertedId().executeAsOne().also { selectRevision(it, chatId, previous.position) }
    } else {
        check(previous.status == "draft") { "The message revision has already been finalized" }
        updateDraft(header, message.status, previous.revision_id)
        previous.revision_id
    }
    // Delivery updates only change the header. The reserved prompt parts stay immutable.
    if (message.expectedRevisionId != null) return revision

    val remaining = ArrayDeque(selectParts(revision).executeAsList())
    val partIds = ArrayList<Long>(message.parts.size)
    for ((index, part) in message.parts.withIndex()) {
        val snapshotId = part.attachment?.let { writeAttachmentSnapshot(it, part.snapshot) }
        partIds += syncPart(
            remaining, Message_part(
                0, revision, part.parent?.let { partIds[it] }, index.toLong(), part.kind, part.title,
                part.status, part.body, part.toolId, part.header, snapshotId,
            ),
        )
    }
    if (remaining.isNotEmpty()) deletePartsFrom(revision, remaining.first().position)
    return revision
}
