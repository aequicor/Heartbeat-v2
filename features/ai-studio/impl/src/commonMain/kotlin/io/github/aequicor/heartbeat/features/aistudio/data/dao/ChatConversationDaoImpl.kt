package io.github.aequicor.heartbeat.features.aistudio.data.dao

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConversationRecord
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatTurnMessageRecord
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Message_part
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Inject
internal class ChatConversationDaoImpl(
    private val connection: DatabaseConnection,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
    @Named("studio-chat-writes") private val writeLock: Mutex,
) : ChatConversationDao {

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun find(sessionId: String): ChatConversationRecord? = withContext(ioDispatcher) {
        accessStudioDatabase("Read chat conversation") {
            ChatQueries(connection.driver).chatConversation(sessionId, ::ChatConversationRecord).executeAsOneOrNull()
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun save(sessionId: String, messages: List<ChatTurnMessageRecord>): Unit = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Save chat turn") {
                val history = ChatQueries(connection.driver)
                history.transaction {
                    val chat = history.chatForSession(sessionId).executeAsOne()
                    for (message in messages) history.writeTurnMessage(chat, message)
                }
            }
        }
    }
}

private fun ChatQueries.writeTurnMessage(chatId: String, message: ChatTurnMessageRecord) {
    val previous = selectMessage(chatId, message.id).executeAsOneOrNull()
    val revision = if (previous == null) {
        insertFirstRevision(message.role, message.status, Clock.System.now().toEpochMilliseconds(), message.id, message.header)
        lastInsertedId().executeAsOne().also { appendEntry(chatId, it) }
    } else {
        check(previous.status == "draft") { "The message revision has already been finalized" }
        updateDraft(message.header, message.status, previous.revision_id)
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
    val mime = if (file.name.endsWith(".md", ignoreCase = true)) "text/markdown" else "text/plain"
    insertInlineAttachment(
        id, file.name, mime, file.content.encodeToByteArray().size.toLong(),
        "sqlite:attachment/$id", file.header, file.content,
    )
    return id
}
