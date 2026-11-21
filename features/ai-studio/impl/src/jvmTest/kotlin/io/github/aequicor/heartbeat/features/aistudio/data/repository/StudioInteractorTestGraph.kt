package io.github.aequicor.heartbeat.features.aistudio.data.repository

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.StudioSessionInteractor

@DependencyGraph(AppScope::class)
internal interface StudioInteractorTestGraph {
    val projects: ProjectInteractor
    val sessions: StudioSessionInteractor

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides connection: DatabaseConnection): StudioInteractorTestGraph
    }
}
