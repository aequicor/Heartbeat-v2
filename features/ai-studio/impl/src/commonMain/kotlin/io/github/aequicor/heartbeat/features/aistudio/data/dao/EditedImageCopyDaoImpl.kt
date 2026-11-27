package io.github.aequicor.heartbeat.features.aistudio.data.dao

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class EditedImageCopyDaoImpl(
    private val connection: DatabaseConnection,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
    @Named("studio-chat-writes") private val writeLock: Mutex,
) : EditedImageCopyDao {

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun save(
        sessionId: String, id: String, name: String, description: String?, path: String, sizeBytes: Long, header: String,
    ): Unit = withContext(ioDispatcher) {
        writeLock.withLock {
            accessStudioDatabase("Save edited image copy") {
                val queries = ChatQueries(connection.driver)
                queries.transaction {
                    val chatId = queries.chatForSession(sessionId).executeAsOne()
                    val existing = queries.selectSentAttachment(id).executeAsOneOrNull()
                    if (existing == null) {
                        val datedHeader = buildJsonObject {
                            Json.parseToJsonElement(header).jsonObject.forEach { (key, value) -> put(key, value) }
                            put("editedAt", queries.editedImageCopyDate().executeAsOne())
                        }
                        queries.insertAttachment(id, name, "image/png", sizeBytes, null, path, datedHeader.toString())
                        queries.insertSentAttachment(
                            id, id, "resource_link", "edited-image:$id", name, description, "image/png", sizeBytes, null, null,
                        )
                    } else {
                        check(existing.uri == "edited-image:$id")
                    }
                    queries.registerEditedImageCopy(chatId, id)
                }
            }
        }
    }
}
