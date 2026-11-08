package io.github.aequicor.heartbeat.features.aistudio.component

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import io.github.aequicor.heartbeat.navigation.decompose.DecomposeFeatureEntry
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot
import kotlinx.coroutines.CoroutineDispatcher

/** Registers the AI studio workspace in the shell feature registry. */
@ContributesIntoSet(AppScope::class)
class AiStudioFeatureEntry : DecomposeFeatureEntry {

    override val featureId: String = AiStudioRoutes.FEATURE_ID

    override fun create(
        route: AppRoute,
        context: ComponentContext,
        host: FeatureHost,
        dispatcher: CoroutineDispatcher,
    ): FeatureRoot = DefaultAiStudioComponent(context, host)
}
