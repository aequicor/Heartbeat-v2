package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatHistoryDataSource
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.ChatHistoryDataSourceImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatHistoryInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatHistoryInteractorImpl
import io.github.aequicor.heartbeat.features.aistudio.data.workspace.StudioWorkspacePersistence
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.WorkspaceHistory
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.StudioWorkspaceStorage
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatConversationDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatConversationDaoImpl
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatHistoryDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ChatHistoryDaoImpl
import io.github.aequicor.heartbeat.features.aistudio.data.dao.ProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightSessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.ChatConversationDataSourceImpl
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.ChatEngineDataSourceImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatTurnInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatTurnInteractorImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ImportChatAttachmentUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ImportChatAttachmentUseCaseImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.SendChatMessageUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.SendChatMessageUseCaseImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
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
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioStoreFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex

@BindingContainer
@ContributesTo(AppScope::class)
public interface DefaultAiStudioInteractorBindings {
    companion object {
        @Provides
        @SingleIn(AppScope::class)
        @Named("studio-chat-writes")
        internal fun provideChatWriteLock(): Mutex = Mutex()

        @Provides
        internal fun provideHistoryDataSource(impl: ChatHistoryDataSourceImpl): ChatHistoryDataSource = impl

        @Provides
        internal fun provideHistoryInteractor(impl: ChatHistoryInteractorImpl): ChatHistoryInteractor = impl

        @Provides
        internal fun provideWorkspaceHistory(
            metadata: StudioWorkspacePersistence,
            history: ChatHistoryInteractor,
            @Named("studio-default") dispatcher: CoroutineDispatcher,
        ): StudioWorkspaceStorage = WorkspaceHistory(metadata, history, dispatcher)

        @Provides
        internal fun provideChatHistoryDao(impl: ChatHistoryDaoImpl): ChatHistoryDao = impl

        @Provides
        internal fun provideChatConversationDao(impl: ChatConversationDaoImpl): ChatConversationDao = impl

        @Provides
        internal fun provideChatConversationDataSource(impl: ChatConversationDataSourceImpl): ChatConversationDataSource = impl

        @Provides
        internal fun provideImportChatAttachmentUseCase(impl: ImportChatAttachmentUseCaseImpl): ImportChatAttachmentUseCase = impl

        @Provides
        internal fun provideSendChatMessageUseCase(impl: SendChatMessageUseCaseImpl): SendChatMessageUseCase = impl

        @Provides
        @SingleIn(AppScope::class)
        internal fun provideChatTurnInteractor(impl: ChatTurnInteractorImpl): ChatTurnInteractor = impl

        @Provides
        internal fun provideChatEngineDataSource(impl: ChatEngineDataSourceImpl): ChatEngineDataSource = impl

        @Provides
        internal fun provideProjectDao(
            connection: DatabaseConnection,
            @Named("io") ioDispatcher: CoroutineDispatcher,
        ): ProjectDao = SqlDelightProjectDao(connection, ioDispatcher)

        @Provides
        internal fun provideSessionDao(
            connection: DatabaseConnection,
            @Named("io") ioDispatcher: CoroutineDispatcher,
        ): SessionDao = SqlDelightSessionDao(connection, ioDispatcher)

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
