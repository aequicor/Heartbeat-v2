package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Page
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

internal interface SessionsDataSource {

    suspend fun <T> transaction(block: suspend () -> T): T

    suspend fun lastConfiguration(): ChatConfiguration?

    @Throwable(StudioRepositoryException::class, CancellationException::class)
    suspend fun rememberSecurityMode(configuration: ChatConfiguration)

    suspend fun create(
        projectId: ProjectId,
        title: String,
        createdAt: Instant,
        parentSessionId: SessionId? = null,
        conversation: ChatConfiguration.Conversation? = null,
    ): Session

    suspend fun findById(id: SessionId): Session?

    suspend fun listRoots(
        projectId: ProjectId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<SessionId>? = null,
    ): Page<Session, SessionId>

    suspend fun listChildren(
        parentSessionId: SessionId,
        status: SessionStatus,
        limit: Int,
        after: PageCursor<SessionId>? = null,
    ): Page<Session, SessionId>

    suspend fun rename(id: SessionId, title: String)

    suspend fun setExpanded(id: SessionId, isExpanded: Boolean)

    suspend fun setStatus(id: SessionId, status: SessionStatus, changedAt: Instant)

    suspend fun delete(id: SessionId)
}
