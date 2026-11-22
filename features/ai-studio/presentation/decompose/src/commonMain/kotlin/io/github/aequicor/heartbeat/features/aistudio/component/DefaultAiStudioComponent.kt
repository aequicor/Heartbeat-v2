package io.github.aequicor.heartbeat.features.aistudio.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioStore
import io.github.aequicor.heartbeat.features.settings.api.SettingsRoutes
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost
import kotlinx.coroutines.CoroutineScope

internal class DefaultAiStudioComponent(
    componentContext: ComponentContext,
    private val host: FeatureHost,
    store: AiStudioStore,
    scope: CoroutineScope,
) : AiStudioComponent, AiStudioStore by store, ComponentContext by componentContext {
    override val projectsSessions: ProjectsSessionsComponent =
        DefaultProjectsSessionsComponent(
            childContext(key = "projects-sessions"),
            requireNotNull(store.projectsSessionsStore),
            scope,
        )

    override fun openSettings() {
        host.open(SettingsRoutes.main())
    }
}
