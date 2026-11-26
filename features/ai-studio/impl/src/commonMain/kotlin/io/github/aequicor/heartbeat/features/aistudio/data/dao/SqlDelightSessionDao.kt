package io.github.aequicor.heartbeat.features.aistudio.data.dao

import app.cash.sqldelight.SuspendingTransacterImpl
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.data.model.SessionDto
import io.github.aequicor.heartbeat.features.aistudio.data.model.ChatConfigRecord
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSecurityMode
import kotlinx.serialization.json.Json
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.StudioSessionQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Studio_session
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

internal class SqlDelightSessionDao(
    private val connection: DatabaseConnection,
    private val ioDispatcher: CoroutineDispatcher,
) : SessionDao {
    private val queries get() = StudioSessionQueries(connection.driver)

    override suspend fun <T> transaction(block: suspend () -> T): T = withContext(ioDispatcher) {
        accessStudioDatabase("Save studio session") {
            val transacter = object : SuspendingTransacterImpl(connection.driver) {}
            transacter.transactionWithResult { block() }
        }
    }

    override suspend fun lastConfiguration(): ChatConfigRecord? = withContext(ioDispatcher) {
        accessStudioDatabase("Read last chat configuration") {
            ChatQueries(connection.driver).lastChatConfiguration(::ChatConfigRecord).executeAsOneOrNull()
        }
    }

    @Throwable(DaoException::class, CancellationException::class)
    override suspend fun rememberSecurityMode(configuration: ChatConfigRecord): Unit = withContext(ioDispatcher) {
        accessStudioDatabase("Save preferred security mode") {
            val chats = ChatQueries(connection.driver)
            chats.transaction {
                chats.rememberChatConfiguration(configuration.engineId, configuration.model, configuration.reasoning)
                chats.rememberSecurityModePreference(configuration.securityModeJson, configuration.engineId)
            }
        }
    }

    override suspend fun insert(
        projectId: String,
        title: String,
        sentAt: DatabaseTimestamp,
        parentSessionId: String?,
        conversation: ChatConfigRecord.Conversation?,
    ): SessionDto = accessStudioDatabase("Insert studio session") {
        val id = Uuid.random().toString()
        val sessionQueries = queries
        sessionQueries.transactionWithResult {
            val owningProjectId = if (parentSessionId == null) {
                sessionQueries.selectSessionProject(projectId).executeAsOneOrNull()
            } else {
                sessionQueries.selectParentProject(parentSessionId).executeAsOneOrNull()
            }
            require(owningProjectId == projectId) {
                "The session requires an existing project and a parent in that project"
            }
            sessionQueries.insertSession(
                id,
                projectId,
                title,
                sentAt.epochSeconds,
                sentAt.nanoseconds,
                parentSessionId,
            )
            if (conversation != null) {
                val config = conversation.configuration
                val chats = ChatQueries(connection.driver)
                val preference = chats.lastSecurityModePreference(config.engineId).executeAsOneOrNull()?.security_config_json
                chats.configureNewChat(config.engineId, config.model, config.reasoning, conversation.engineSessionId, id)
                chats.rememberChatConfiguration(config.engineId, config.model, config.reasoning)
                val securityMode = config.securityModeJson?.let { Json.decodeFromString<ChatSecurityMode>(it) }
                    ?: preference?.let { Json.decodeFromString<ChatSecurityMode.Preference>(it).forNewChat() }
                if (securityMode != null) {
                    chats.setChatSecurityMode(Json.encodeToString(securityMode), id, config.engineId, conversation.engineSessionId, null)
                    securityMode.selectedValue?.let { value ->
                        val next = ChatSecurityMode.Preference(securityMode.configId, securityMode.options, value)
                        chats.rememberSecurityModePreference(Json.encodeToString(next), config.engineId)
                    }
                }
            }
            sessionQueries.selectSessionById(id).executeAsOne().toSessionDto()
        }
    }

    override suspend fun selectById(id: String): SessionDto? =
        accessStudioDatabase("Select studio session by ID") {
            queries.selectSessionById(id).executeAsOneOrNull()?.toSessionDto()
        }

    override suspend fun selectRoots(
        projectId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor?,
    ): DaoPage<SessionDto> {
        require(limit > 0)
        return accessStudioDatabase("Select root studio sessions") {
            val rows = queries.selectRootSessions(
                projectId,
                status,
                after?.id,
                after?.updatedAt?.epochSeconds ?: 0L,
                after?.updatedAt?.nanoseconds ?: 0L,
                limit.toLong() + 1,
            ).executeAsList().map(Studio_session::toSessionDto)
            studioPage(rows, limit, SessionDto::toCursor)
        }
    }

    override suspend fun selectChildren(
        parentSessionId: String,
        status: String,
        limit: Int,
        after: DaoPageCursor?,
    ): DaoPage<SessionDto> {
        require(limit > 0)
        return accessStudioDatabase("Select child studio sessions") {
            val rows = queries.selectChildSessions(
                parentSessionId,
                status,
                after?.id,
                after?.updatedAt?.epochSeconds ?: 0L,
                after?.updatedAt?.nanoseconds ?: 0L,
                limit.toLong() + 1,
            ).executeAsList().map(Studio_session::toSessionDto)
            studioPage(rows, limit, SessionDto::toCursor)
        }
    }

    override suspend fun updateTitle(id: String, title: String) {
        accessStudioDatabase("Update studio session title") { queries.updateSessionTitle(title, id) }
    }

    override suspend fun updateExpanded(id: String, isExpanded: Boolean) {
        accessStudioDatabase("Update session expansion") {
            queries.updateSessionExpanded(if (isExpanded) 1L else 0L, id)
        }
    }

    override suspend fun updateStatus(id: String, status: String, changedAt: DatabaseTimestamp) {
        accessStudioDatabase("Update studio session status") {
            queries.updateSessionStatus(status, changedAt.epochSeconds, changedAt.nanoseconds, id)
        }
    }

    override suspend fun delete(id: String): Unit = withContext(ioDispatcher) {
        accessStudioDatabase("Delete studio session tree") {
            queries.transaction {
                queries.deleteSessionTree(id)
                ChatQueries(connection.driver).clearUnreferencedHistory()
            }
        }
    }

    /** Updates the session's activity timestamp if newer, joining the current transaction when present. */
    fun updateActivity(id: String, at: DatabaseTimestamp) {
        accessStudioDatabase("Update studio session activity") {
            queries.updateSessionActivity(at.epochSeconds, at.nanoseconds, id)
        }
    }
}

private fun Studio_session.toSessionDto() = SessionDto(
    id,
    project_id,
    title,
    status,
    DatabaseTimestamp(created_at_seconds, created_at_nanos),
    DatabaseTimestamp(updated_at_seconds, updated_at_nanos),
    parent_session_id,
    isExpanded = is_expanded == 1L,
    workState = work_state,
)

private fun SessionDto.toCursor() = DaoPageCursor(updatedAt, id)
