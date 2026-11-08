package io.github.aequicor.heartbeat.shell

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.value.Value
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import io.github.aequicor.heartbeat.navigation.decompose.DecomposeFeatureEntry
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Default shell navigation: a single workspace stack of [AppRoute] configurations. The child
 * factory resolves the feature entry by `featureId` and creates the feature root, so the shell
 * never references a concrete feature component.
 */
internal class DefaultShellComponent(
    componentContext: ComponentContext,
    initialRoute: AppRoute,
    entries: Set<DecomposeFeatureEntry>,
    private val dispatcher: CoroutineDispatcher,
) : ShellComponent, ComponentContext by componentContext {

    private val registry = FeatureRegistry(entries)

    private val navigation = StackNavigation<AppRoute>()

    private val host: FeatureHost = object : FeatureHost {
        override fun open(route: AppRoute) {
            navigation.navigate { stack ->
                if (stack.last() == route) stack else stack + route
            }
        }

        override fun back() {
            navigation.pop()
        }
    }

    override val childStack: Value<ChildStack<AppRoute, FeatureRoot>> = childStack(
        source = navigation,
        serializer = AppRoute.serializer,
        initialConfiguration = initialRoute,
        key = "shell",
        childFactory = { route, context ->
            val entry = registry.find(route.featureId)
            entry?.create(
                route = route,
                context = context,
                host = host,
                dispatcher = dispatcher,
            ) ?: MissingFeatureRoot(route.featureId)
        },
    )

    override fun open(route: AppRoute) {
        host.open(route)
    }

    override fun back(): Boolean {
        if (childStack.value.backStack.isEmpty()) {
            return false
        }

        navigation.pop()
        return true
    }
}
