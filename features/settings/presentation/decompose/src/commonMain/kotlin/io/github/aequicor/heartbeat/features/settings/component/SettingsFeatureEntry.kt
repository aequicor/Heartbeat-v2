package io.github.aequicor.heartbeat.features.settings.component

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.settings.api.SettingsRoutes
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import io.github.aequicor.heartbeat.navigation.decompose.DecomposeFeatureEntry
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot
import kotlinx.coroutines.CoroutineDispatcher

/** Registers the settings shell in the shell feature registry. */
@ContributesIntoSet(AppScope::class)
class SettingsFeatureEntry(
    private val factory: SettingsComponentFactory,
) : DecomposeFeatureEntry {

    override val featureId: String = SettingsRoutes.FEATURE_ID

    override fun create(
        route: AppRoute,
        context: ComponentContext,
        host: FeatureHost,
        dispatcher: CoroutineDispatcher,
    ): FeatureRoot = factory.create(
        componentContext = context,
        requestedPageId = route.destination.takeUnless { it == "root" },
        onBack = host::back,
    )
}
