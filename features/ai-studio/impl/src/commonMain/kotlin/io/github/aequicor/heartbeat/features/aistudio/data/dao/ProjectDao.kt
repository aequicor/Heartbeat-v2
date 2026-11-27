package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.exception.ProjectNameAlreadyExistsException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor
import io.github.aequicor.heartbeat.features.aistudio.data.model.ProjectDto
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ProjectDao {

    data class Registration(val project: ProjectDto, val created: Boolean)

    @Throwable(DaoException::class, ProjectNameAlreadyExistsException::class, CancellationException::class)
    suspend fun insert(immutableName: String, workingDirectory: String): Registration

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun selectById(id: String): ProjectDto?

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun select(
        status: String,
        limit: Int,
        after: DaoPageCursor? = null,
    ): DaoPage<ProjectDto>

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun updateTitle(id: String, title: String)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun updateExpanded(id: String, isExpanded: Boolean)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun updateStatus(id: String, status: String)

    @Throwable(DaoException::class, CancellationException::class)
    suspend fun delete(id: String)
}
