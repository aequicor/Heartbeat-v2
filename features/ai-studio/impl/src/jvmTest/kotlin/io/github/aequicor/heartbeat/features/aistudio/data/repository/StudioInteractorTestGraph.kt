package io.github.aequicor.heartbeat.features.aistudio.data.repository

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectsSessionsRepository
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ObserveProjectsSessionsUseCase

@DependencyGraph(AppScope::class)
internal interface StudioInteractorTestGraph {
    val catalog: ProjectsSessionsInteractor
    val projectDataSource: ProjectDataSource
    val observeProjectsSessions: ObserveProjectsSessionsUseCase
    val sessionBootstrap: SessionBootstrapInteractor
    val repository: ProjectsSessionsRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides connection: DatabaseConnection): StudioInteractorTestGraph
    }
}
