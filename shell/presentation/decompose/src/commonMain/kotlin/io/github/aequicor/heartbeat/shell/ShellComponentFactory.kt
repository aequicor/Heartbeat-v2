package io.github.aequicor.heartbeat.shell

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import io.github.aequicor.heartbeat.navigation.decompose.DecomposeFeatureEntry
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Creates the shell root. [ComponentContext] and the initial [AppRoute] are runtime parameters, so
 * they stay outside the Metro graph; the feature entries arrive as a Metro set contribution.
 */
interface ShellComponentFactory {

    fun create(
        componentContext: ComponentContext,
        initialRoute: AppRoute,
        dispatcher: CoroutineDispatcher,
    ): ShellComponent
}

@ContributesBinding(AppScope::class)
class DefaultShellComponentFactory(
    private val entries: Set<DecomposeFeatureEntry>,
) : ShellComponentFactory {

    override fun create(
        componentContext: ComponentContext,
        initialRoute: AppRoute,
        dispatcher: CoroutineDispatcher,
    ): ShellComponent =
        DefaultShellComponent(
            componentContext = componentContext,
            initialRoute = initialRoute,
            entries = entries,
            dispatcher = dispatcher,
        )
}
