package io.github.aequicor.heartbeat.shell

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot

/**
 * Root navigation of the application: the shell owns the workspace stack and the layout, while
 * each feature owns its screens.
 */
interface ShellComponent {

    val childStack: Value<ChildStack<AppRoute, FeatureRoot>>

    /** Opens a route. Inter-feature navigation goes through this method. */
    fun open(route: AppRoute)

    /** Handles Back: returns `false` when the shell stack has nothing to pop. */
    fun back(): Boolean
}
