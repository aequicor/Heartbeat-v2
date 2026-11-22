package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.data.workspace.StudioWorkspacePersistence
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ObserveProjectsSessionsUseCase
import io.github.aequicor.heartbeat.features.aistudio.presentation.DefaultAiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStoreFactory
import kotlinx.coroutines.CoroutineScope

internal class DefaultAiStudioStoreFactory @Inject constructor(
    private val preferences: PreferencesStoreFactory,
    private val projectsSessions: ProjectsSessionsInteractor,
    private val observeProjectsSessions: ObserveProjectsSessionsUseCase,
    private val sessionBootstrap: SessionBootstrapInteractor,
    private val persistence: StudioWorkspacePersistence,
) : AiStudioStoreFactory {

    override fun create(scope: CoroutineScope): AiStudioStore =
        DefaultAiStudioStore(
            preferences.forNamespace("aurora"),
            scope,
            projectsSessions,
            persistence,
            observeProjectsSessions(),
            sessionBootstrap,
        )
}
