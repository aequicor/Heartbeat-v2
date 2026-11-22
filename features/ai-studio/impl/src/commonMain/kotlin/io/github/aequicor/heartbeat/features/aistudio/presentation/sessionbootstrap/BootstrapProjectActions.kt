package io.github.aequicor.heartbeat.features.aistudio.presentation.sessionbootstrap

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PageCursor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction.BootstrapProject
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.SessionBootstrapState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.Path.Companion.toPath

internal suspend fun loadBootstrapProjects(
    screen: MutableStateFlow<AiStudioScreenState>,
    catalog: ProjectsSessionsInteractor?,
) {
    val bootstrap = screen.value.sessionBootstrap ?: return
    if (catalog == null || bootstrap.projects is SessionBootstrapState.ProjectList.Ready) return
    screen.update {
        it.copy(sessionBootstrap = bootstrap.copy(projects = SessionBootstrapState.ProjectList.Loading))
    }
    val projects = try {
        val choices = buildList {
            var cursor: PageCursor<ProjectId>? = null
            do {
                val page = catalog.listProjects(ProjectStatus.ACTIVE, after = cursor)
                page.items.filterIsInstance<Project.WithDirectory>().forEach { project ->
                    add(SessionBootstrapState.ProjectChoice(project.id.value.toString(), project.title))
                }
                cursor = page.nextCursor
            } while (cursor != null)
        }
        SessionBootstrapState.ProjectList.Ready(choices)
    } catch (_: StudioRepositoryException) {
        SessionBootstrapState.ProjectList.Failed
    }
    screen.update { it.copy(sessionBootstrap = bootstrap.copy(projects = projects)) }
}

internal suspend fun updateBootstrapProject(
    screen: MutableStateFlow<AiStudioScreenState>,
    interactor: SessionBootstrapInteractor?,
    action: BootstrapProject,
) {
    val state = screen.value.sessionBootstrap ?: return
    if (interactor == null) return
    if (action is BootstrapProject.Add || action is BootstrapProject.Retry) {
        screen.update {
            it.copy(sessionBootstrap = state.copy(operation = SessionBootstrapState.Operation.RegisteringProject))
        }
    }
    val next = applyBootstrapProjectAction(interactor, state, action)
    screen.update { it.copy(sessionBootstrap = next) }
}

private suspend fun applyBootstrapProjectAction(
    bootstrap: SessionBootstrapInteractor,
    state: SessionBootstrapState,
    action: BootstrapProject,
): SessionBootstrapState = when (action) {
    is BootstrapProject.Add -> createProject(bootstrap, state, action.workingDirectoryPath, action.displayName)
    is BootstrapProject.Select -> state.copy(
        project = action.project, projectTitle = action.title, error = null, pendingProject = null,
    )
    is BootstrapProject.Retry -> state.pendingProject?.let {
        createProject(bootstrap, state, it.workingDirectoryPath, action.name)
    } ?: state
    BootstrapProject.Cancel -> state.copy(error = null, pendingProject = null)
}

private suspend fun createProject(
    bootstrap: SessionBootstrapInteractor,
    state: SessionBootstrapState,
    directory: String,
    name: String? = null,
): SessionBootstrapState = try {
    val projectDirectory = if (directory.startsWith("content://")) {
        ProjectDirectory.DocumentTree(directory)
    } else {
        val path = directory.toPath(normalize = true)
        require(name == null || path.isAbsolute)
        ProjectDirectory.Local(path)
    }
    when (val result = bootstrap.registerProject(projectDirectory, name)) {
        is ProjectCreationResult.Registered -> state.selectRegisteredProject(result.project)
        is ProjectCreationResult.NameConflict -> state.copy(
            error = SessionBootstrapState.Error.ProjectNameTaken(result.immutableName),
            pendingProject = SessionBootstrapState.PendingProject(directory, result.immutableName),
        )
        ProjectCreationResult.InvalidWorkingDirectory ->
            state.copy(error = SessionBootstrapState.Error.InvalidWorkingDirectory)
        ProjectCreationResult.InvalidName -> state.copy(error = SessionBootstrapState.Error.InvalidName)
    }
} catch (_: IllegalArgumentException) {
    state.copy(error = SessionBootstrapState.Error.InvalidWorkingDirectory)
} catch (_: StudioRepositoryException) {
    state.copy(error = SessionBootstrapState.Error.StorageFailure)
}

private fun SessionBootstrapState.selectRegisteredProject(project: Project.WithDirectory): SessionBootstrapState {
    val id = project.id.value.toString()
    val choice = SessionBootstrapState.ProjectChoice(id, project.title)
    val loaded = projects as? SessionBootstrapState.ProjectList.Ready
    return copy(
        project = SessionBootstrapState.Project.Existing(id),
        projectTitle = project.title,
        error = null,
        pendingProject = null,
        projects = loaded?.copy(choices = listOf(choice) + loaded.choices.filterNot { it.id == id }) ?: projects,
    )
}
