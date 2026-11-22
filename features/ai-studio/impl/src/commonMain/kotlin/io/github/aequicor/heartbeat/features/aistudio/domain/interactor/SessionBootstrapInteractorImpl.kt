package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectsSessionsRepository
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.Throws as Throwable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Inject
internal class SessionBootstrapInteractorImpl(
    private val repository: ProjectsSessionsRepository,
    private val projects: ProjectDataSource,
    private val sessions: SessionsDataSource,
    private val runtime: ChatEngineDataSource,
) : SessionBootstrapInteractor {

    @Throwable(CancellationException::class)
    override suspend fun registerProject(directory: ProjectDirectory, name: String?): Result<ProjectCreationResult> = runCatching {
        val path = (directory as? ProjectDirectory.Local)?.path
        val projectName = (name ?: path?.name).orEmpty().trim()
        when {
            path != null && !isValidName(path.name) -> ProjectCreationResult.InvalidWorkingDirectory
            !isValidName(projectName) -> ProjectCreationResult.InvalidName
            else -> repository.registerProject(projectName, directory)
        }
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun withDraft(projectId: ProjectId, use: suspend (ChatCreationDraft) -> Unit): Result<Unit> =
        runCatching {
            val draft = ChatCreationDraftImpl(projectId, projects, sessions, runtime, repository)
            try {
                coroutineScope {
                    val catalog = launch { draft.observeCatalog() }
                    try { use(draft) } finally { catalog.cancel() }
                }
            } finally {
                withContext(NonCancellable) { draft.close() }
            }
        }.rethrowCancellation()

    private fun isValidName(name: String) = name.isNotBlank() && name != "." && name != ".."
}
