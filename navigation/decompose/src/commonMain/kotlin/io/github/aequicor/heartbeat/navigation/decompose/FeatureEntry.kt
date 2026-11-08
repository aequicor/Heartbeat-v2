package io.github.aequicor.heartbeat.navigation.decompose

import com.arkivanov.decompose.ComponentContext
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import kotlinx.coroutines.CoroutineDispatcher

/** Root component of a feature. The shell keeps it opaque and never inspects feature screens. */
interface FeatureRoot

/**
 * Minimal port of inter-feature navigation. It does not repeat `StackNavigation`: only operations
 * that belong to the shell level are exposed.
 */
interface FeatureHost {

    fun open(route: AppRoute)

    fun back()
}

/**
 * Factory of a feature root component. The shell resolves an entry by `featureId` and creates the
 * root with the runtime parameters that cannot live in the Metro graph.
 */
interface DecomposeFeatureEntry {

    val featureId: String

    fun create(
        route: AppRoute,
        context: ComponentContext,
        host: FeatureHost,
        dispatcher: CoroutineDispatcher,
    ): FeatureRoot
}
