package io.github.aequicor.heartbeat.features.aistudio.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioStoreFactory
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import io.github.aequicor.heartbeat.navigation.decompose.DecomposeFeatureEntry
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot
import io.github.aequicor.heartbeat.navigation.decompose.WorkspaceRegistration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

/** Registers the AI studio workspace in the shell feature registry. */
@ContributesIntoSet(AppScope::class)
class AiStudioFeatureEntry @Inject internal constructor(
    private val storeFactory: AiStudioStoreFactory,
    private val appScope: AppCoroutineScope,
    @Named("ai-studio-worker") private val worker: CoroutineDispatcher,
) : DecomposeFeatureEntry {

    override val featureId: String = AiStudioRoutes.FEATURE_ID

    override val workspace = WorkspaceRegistration(
        route = AiStudioRoutes.main(),
        label = "Чат",
        icon = HbIconGlyph.ChatWorkspace,
        priority = 0,
    )

    override fun create(
        route: AppRoute,
        context: ComponentContext,
        host: FeatureHost,
        dispatcher: CoroutineDispatcher,
    ): FeatureRoot {
        val scope = CoroutineScope(SupervisorJob(appScope.coroutineContext.job) + worker)
        context.lifecycle.doOnDestroy(scope::cancel)
        return DefaultAiStudioComponent(context, host, storeFactory.create(scope), scope)
    }
}
