package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightStudioSessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.repository.DefaultProjectRepository
import io.github.aequicor.heartbeat.features.aistudio.data.repository.DefaultStudioSessionRepository
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.StudioSessionInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectRepository
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.StudioSessionRepository

@BindingContainer
@ContributesTo(AppScope::class)
internal interface DefaultAiStudioInteractorBindings {
    companion object {
        @Provides
        fun provideProjectRepository(connection: DatabaseConnection): ProjectRepository =
            DefaultProjectRepository(SqlDelightProjectDao(connection))

        @Provides
        fun provideStudioSessionRepository(connection: DatabaseConnection): StudioSessionRepository =
            DefaultStudioSessionRepository(SqlDelightStudioSessionDao(connection))

        @Provides
        fun provideProjectInteractor(repository: ProjectRepository): ProjectInteractor = ProjectInteractor(repository)

        @Provides
        fun provideStudioSessionInteractor(repository: StudioSessionRepository): StudioSessionInteractor =
            StudioSessionInteractor(repository)
    }
}
