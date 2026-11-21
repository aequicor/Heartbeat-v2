package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.exception.ProjectNameAlreadyExistsException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.ProjectDto
import kotlin.coroutines.cancellation.CancellationException

internal interface ProjectDao {

    @Throws(DaoException::class, ProjectNameAlreadyExistsException::class, CancellationException::class)
    suspend fun create(immutableName: String, workingDirectory: String): ProjectDto

    @Throws(DaoException::class, CancellationException::class)
    suspend fun findById(id: String): ProjectDto?

    @Throws(DaoException::class, CancellationException::class)
    suspend fun list(
        status: String,
        limit: Int,
        after: DaoPageCursor? = null,
    ): DaoPage<ProjectDto>

    @Throws(DaoException::class, CancellationException::class)
    suspend fun renameTitle(id: String, title: String)

    @Throws(DaoException::class, CancellationException::class)
    suspend fun setExpanded(id: String, isExpanded: Boolean)

    @Throws(DaoException::class, CancellationException::class)
    suspend fun setStatus(id: String, status: String)
}
