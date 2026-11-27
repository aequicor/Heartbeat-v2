package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.StudioWorkspaceStorage
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatTurnInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ImportChatAttachmentUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.FindPromptReferencesUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ObserveProjectsSessionsUseCase
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.DefaultAiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.DraftAttachmentImageLoader
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AttachmentReferenceResolver
import dev.zacsweers.metro.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class DefaultAiStudioStoreFactory @Inject constructor(
    private val preferences: PreferencesStoreFactory,
    private val projectsSessions: ProjectsSessionsInteractor,
    private val observeProjectsSessions: ObserveProjectsSessionsUseCase,
    private val sessionBootstrap: SessionBootstrapInteractor,
    private val persistence: StudioWorkspaceStorage,
    @Named("studio-default") private val defaultDispatcher: CoroutineDispatcher,
    private val chatTurn: ChatTurnInteractor,
    private val importAttachment: ImportChatAttachmentUseCase,
    private val findPromptReferences: FindPromptReferencesUseCase,
    private val attachmentReferences: AttachmentReferenceResolver,
    private val imageLoader: DraftAttachmentImageLoader,
) : AiStudioStoreFactory {

    override fun create(scope: CoroutineScope): AiStudioStore =
        DefaultAiStudioStore(
            preferences.forNamespace("aurora"),
            scope,
            projectsSessions,
            persistence,
            observeProjectsSessions(),
            sessionBootstrap,
            defaultDispatcher,
            chatTurn,
            importAttachment,
            findPromptReferences,
            attachmentReferences,
            imageLoader,
        )
}
