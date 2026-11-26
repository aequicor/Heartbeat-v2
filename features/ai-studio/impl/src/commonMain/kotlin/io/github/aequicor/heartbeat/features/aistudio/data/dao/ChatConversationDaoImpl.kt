package io.github.aequicor.heartbeat.features.aistudio.data.dao

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
    override suspend fun reserveRetry(sessionId: String, messageId: String): ChatEntryRecord? = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Reserve prompt retry") {
                val history = ChatQueries(connection.driver)
                val chat = history.chatForSession(sessionId).executeAsOne()
                val failed = history.failedUserMessage(chat, messageId).executeAsOneOrNull()
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
                    ).value
                    if (inserted != 1L) return@transactionWithResult null
                    val revision = history.lastInsertedId().executeAsOne()
                    history.copyPromptParts(revision, failed.revision_id)
                    history.selectRevision(revision, chat, failed.position)
                    val parts = history.selectParts(revision).executeAsList().map { part ->
                        ChatEntryRecord.Part(
                            part.id, null, part.kind, part.title, part.body, part.presentation,
                            part.attachment_id?.let { history.fullAttachment(it, forRetry = true) }, deferred = false,
                        )
                    }
                    ChatEntryRecord(failed.position, revision, failed.message_id, false, "user", header, parts)
                }
            }
        }
    }
}

private fun ChatQueries.writeTurnMessage(chatId: String, message: ChatTurnMessageRecord, owner: Uuid?) {
    val header = if (message.status == "draft" && owner != null) message.header.withDraftOwner(owner) else message.header
    // A write as the first statement lets SQLite wait for another writer before taking a read snapshot.
    insertFirstRevision(message.role, message.status, Clock.System.now().toEpochMilliseconds(), message.id, header, chatId)
    val previous = selectMessage(chatId, message.id).executeAsOneOrNull()
    val revision = if (previous == null) {
        lastInsertedId().executeAsOne().also { appendEntry(chatId, it) }
    } else {
        check(previous.status == "draft") { "The message revision has already been finalized" }
        updateDraft(header, message.status, previous.revision_id)
        previous.revision_id
    }
    val remaining = ArrayDeque(selectParts(revision).executeAsList())
    val partIds = ArrayList<Long>(message.parts.size)
    for ((index, part) in message.parts.withIndex()) {
        partIds += syncPart(
            remaining, Message_part(
                0, revision, part.parent?.let { partIds[it] }, index.toLong(), part.kind, part.title,
                part.status, part.body, part.toolId, part.attachment?.let { recordAttachment(it) }, part.header,
            ),
        )
    }
    if (remaining.isNotEmpty()) deletePartsFrom(revision, remaining.first().position)
}

private fun ChatQueries.recordAttachment(file: ChatTurnMessageRecord.Attachment): String {
    val existing = file.existingId ?: findInlineAttachment(file.header, file.content).executeAsOneOrNull()
    if (existing != null) return existing
    val id = Uuid.random().toString()
    insertInlineAttachment(
        id, file.name, file.mimeType, file.size,
        "sqlite:attachment/$id", file.header, file.content,
    )
    return id
}
