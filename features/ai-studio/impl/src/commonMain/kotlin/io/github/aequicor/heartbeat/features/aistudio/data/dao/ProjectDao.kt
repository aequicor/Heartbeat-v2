package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.exception.ProjectNameAlreadyExistsException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.ProjectDto
import kotlin.coroutines.cancellation.CancellationException

internal interface ProjectDao {

    @Throws(DaoException::class, ProjectNameAlreadyExistsException::class, CancellationException::class)
    suspend fun insert(immutableName: String, workingDirectory: String): ProjectDto

    @Throws(DaoException::class, CancellationException::class)
    suspend fun selectById(id: String): ProjectDto?

    @Throws(DaoException::class, CancellationException::class)
    suspend fun select(
        status: String,
        limit: Int,
        after: DaoPageCursor? = null,
    ): DaoPage<ProjectDto>

    @Throws(DaoException::class, CancellationException::class)
    suspend fun updateTitle(id: String, title: String)

    @Throws(DaoException::class, CancellationException::class)
    suspend fun updateExpanded(id: String, isExpanded: Boolean)

    @Throws(DaoException::class, CancellationException::class)
    suspend fun updateStatus(id: String, status: String)

    @Throws(DaoException::class, CancellationException::class)
    suspend fun delete(id: String)
}
