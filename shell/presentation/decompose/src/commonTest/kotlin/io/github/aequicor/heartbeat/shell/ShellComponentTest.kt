package io.github.aequicor.heartbeat.shell

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import io.github.aequicor.heartbeat.navigation.api.AppRoute
import io.github.aequicor.heartbeat.navigation.decompose.DecomposeFeatureEntry
import io.github.aequicor.heartbeat.navigation.decompose.FeatureHost
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellComponentTest {

    private val start = AppRoute(featureId = "start")

    private val next = AppRoute(featureId = "next")

    @Test
    fun startsAtTheInitialRouteAndNavigatesThroughTheRegistry() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val shell = DefaultShellComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialRoute = start,
            entries = setOf(entry("start"), entry("next")),
            dispatcher = UnconfinedTestDispatcher(),
        )

        try {
            assertEquals(start, shell.childStack.value.active.configuration)

            shell.open(next)

            assertEquals(next, shell.childStack.value.active.configuration)

            assertTrue(shell.back())
            assertEquals(start, shell.childStack.value.active.configuration)
            assertFalse(shell.back())
        } finally {
            lifecycle.destroy()
        }
    }

    @Test
    fun unknownRouteKeepsTheShellUsable() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val shell = DefaultShellComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialRoute = AppRoute(featureId = "removed"),
            entries = setOf(entry("start")),
            dispatcher = UnconfinedTestDispatcher(),
        )

        try {
            val active = shell.childStack.value.active
            assertEquals("removed", active.configuration.featureId)
            assertEquals(MissingFeatureRoot("removed"), active.instance)
        } finally {
            lifecycle.destroy()
        }
    }

    @Test
    fun registryRejectsDuplicateFeatures() {
        assertFailsWith<IllegalArgumentException> {
            FeatureRegistry(setOf(entry("same"), entry("same")))
        }
    }

    @Test
    fun registryRejectsUnknownFeature() {
        assertFailsWith<IllegalArgumentException> {
            FeatureRegistry(setOf(entry("known"))).resolve("unknown")
        }
    }

    @Test
    fun featureHostBackAtRootDoesNotEmptyTheStack() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()

        var host: FeatureHost? = null
        val shell = DefaultShellComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialRoute = start,
            entries = setOf(entryCapturingHost("start") { host = it }),
            dispatcher = UnconfinedTestDispatcher(),
        )

        try {
            assertEquals(start, shell.childStack.value.active.configuration)

            // A feature requesting Back at the shell root must not drop the only configuration:
            // an empty ChildStack would make `active` throw on the next access.
            checkNotNull(host).back()

            assertEquals(start, shell.childStack.value.active.configuration)
        } finally {
            lifecycle.destroy()
        }
    }

    @Test
    fun openingARouteAlreadyInTheStackReusesItInsteadOfPushingADuplicate() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val shell = DefaultShellComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialRoute = start,
            entries = setOf(entry("start"), entry("next")),
            dispatcher = UnconfinedTestDispatcher(),
        )

        try {
            shell.open(next)
            shell.open(start) // `start` is now below the top; Decompose rejects duplicate configs

            assertEquals(start, shell.childStack.value.active.configuration)
            assertEquals(1, shell.childStack.value.backStack.size)
        } finally {
            lifecycle.destroy()
        }
    }

    @Test
    fun staleFeatureHostCannotMutateTheActiveStack() {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()

        var startHost: FeatureHost? = null
        val shell = DefaultShellComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialRoute = start,
            entries = setOf(
                entryCapturingHost("start") { startHost = it },
                entry("next"),
                entry("other"),
            ),
            dispatcher = UnconfinedTestDispatcher(),
        )

        try {
            val captured = checkNotNull(startHost)
            shell.open(next)

            captured.open(AppRoute(featureId = "other"))

            assertEquals(next, shell.childStack.value.active.configuration)
            assertEquals(1, shell.childStack.value.backStack.size)
        } finally {
            lifecycle.destroy()
        }
    }

    private fun entry(featureId: String): DecomposeFeatureEntry =
        object : DecomposeFeatureEntry {
            override val featureId: String = featureId

            override fun create(
                route: AppRoute,
                context: ComponentContext,
                host: FeatureHost,
                dispatcher: CoroutineDispatcher,
            ): FeatureRoot = object : FeatureRoot {}
        }

    private fun entryCapturingHost(
        featureId: String,
        onHost: (FeatureHost) -> Unit,
    ): DecomposeFeatureEntry =
        object : DecomposeFeatureEntry {
            override val featureId: String = featureId

            override fun create(
                route: AppRoute,
                context: ComponentContext,
                host: FeatureHost,
                dispatcher: CoroutineDispatcher,
            ): FeatureRoot {
                onHost(host)
                return object : FeatureRoot {}
            }
        }
}
