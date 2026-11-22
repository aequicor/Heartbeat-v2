package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectsSessionsChange
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectsSessionsRepository
import kotlinx.coroutines.flow.SharedFlow

@Inject
internal class ObserveProjectsSessionsUseCase(private val repository: ProjectsSessionsRepository) {

    operator fun invoke(): SharedFlow<ProjectsSessionsChange> = repository.observeChanges()
}
