package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightSessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.DefaultProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.DefaultSessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.SqlDelightProjectsSessionsSearchDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectsSessionsSearchDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractorImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractorImpl
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStoreFactory

@BindingContainer
@ContributesTo(AppScope::class)
public interface DefaultAiStudioInteractorBindings {
    companion object {
        @Provides
        internal fun provideProjectDao(connection: DatabaseConnection): ProjectDao = SqlDelightProjectDao(connection)

        @Provides
        internal fun provideSessionDao(connection: DatabaseConnection): SessionDao = SqlDelightSessionDao(connection)

        @Provides
        internal fun provideProjectDataSource(dataSource: DefaultProjectDataSource): ProjectDataSource = dataSource

        @Provides
        internal fun provideSessionsDataSource(dataSource: DefaultSessionsDataSource): SessionsDataSource = dataSource

        @Provides
        internal fun provideProjectsSessionsSearchDataSource(
            dataSource: SqlDelightProjectsSessionsSearchDataSource,
        ): ProjectsSessionsSearchDataSource = dataSource

        @Provides
        internal fun provideProjectsSessionsInteractor(
            interactor: ProjectsSessionsInteractorImpl,
        ): ProjectsSessionsInteractor = interactor

        @Provides
        internal fun provideSessionBootstrapInteractor(
            interactor: SessionBootstrapInteractorImpl,
        ): SessionBootstrapInteractor = interactor

        @Provides
        internal fun provideAiStudioStoreFactory(factory: DefaultAiStudioStoreFactory): AiStudioStoreFactory = factory
    }
}
