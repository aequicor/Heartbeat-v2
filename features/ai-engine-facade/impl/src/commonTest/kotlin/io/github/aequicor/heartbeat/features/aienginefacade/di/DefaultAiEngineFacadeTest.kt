package io.github.aequicor.heartbeat.features.aienginefacade.di

import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAiEngineFacadeTest {

    @Test
    fun catalogIsLazyAndUsesEngineIds() = runTest {
        val provider = TestEngineProvider(TestEngineKey("test"))
        val scope = AppCoroutineScope(backgroundScope)
        val facade = DefaultAiEngineFacade(setOf(provider), scope)

        assertEquals(listOf(provider.key), facade.availableEngines.value)
        assertEquals(mapOf(provider.key to AiEngineState.NotInitialized), facade.engines.value)
        assertEquals(0, provider.creations)
        assertSame(facade.getEngine(provider.key), facade.getEngine(TestEngineKey("test", "Other name")))
        assertEquals(1, provider.creations)
        assertFailsWith<IllegalArgumentException> { facade.getEngine(TestEngineKey("unknown")) }
        assertFailsWith<IllegalArgumentException> {
            DefaultAiEngineFacade(setOf(provider, TestEngineProvider(TestEngineKey("test"))), scope)
        }
    }

    @Test
    fun aggregationPublishesTheLatestStateAtTheEndOfTheWindow() = runTest {
        val provider = TestEngineProvider(TestEngineKey("test"))
        val facade = DefaultAiEngineFacade(setOf(provider), AppCoroutineScope(backgroundScope))
        val engine = facade.getEngine(provider.key)
        val runtime = assertNotNull(provider.latestRuntime)

        runtime.state.value = AiEngineState.Connecting
        runCurrent()
        advanceTimeBy(STATE_WINDOW - 1)
        runtime.state.value = AiEngineState.Ready
        assertEquals(AiEngineState.Ready, engine.state.value)
        assertEquals(AiEngineState.NotInitialized, facade.engines.value[provider.key])

        advanceTimeBy(1)
        runCurrent()
        assertEquals(AiEngineState.Ready, facade.engines.value[provider.key])

        engine.dispose()
        runtime.state.value = AiEngineState.Failed("Stale instance")
        runCurrent()
        advanceTimeBy(STATE_WINDOW)
        runCurrent()
        assertEquals(AiEngineState.NotInitialized, facade.engines.value[provider.key])
    }

    @Test
    fun disposingAnEnginePreventsAccessUntilItCanBeRecreated() = runTest {
        val canDispose = CompletableDeferred<Unit>()
        val provider = TestEngineProvider(TestEngineKey("test")) { TestEngine(it) { canDispose.await() } }
        val facade = DefaultAiEngineFacade(setOf(provider), AppCoroutineScope(backgroundScope))
        val original = facade.getEngine(provider.key)
        val originalRuntime = assertNotNull(provider.latestRuntime)
        val disposing = launch { original.dispose() }
        runCurrent()

        assertFailsWith<IllegalStateException> { facade.getEngine(provider.key) }
        canDispose.complete(Unit)
        disposing.join()

        val replacement = facade.getEngine(provider.key)
        val replacementRuntime = assertNotNull(provider.latestRuntime)
        assertNotSame(original, replacement)
        original.dispose()
        assertSame(replacement, facade.getEngine(provider.key))
        assertEquals(1, originalRuntime.disposals)
        assertEquals(0, replacementRuntime.disposals)
        facade.dispose()
        assertEquals(1, replacementRuntime.disposals)
        assertFailsWith<IllegalStateException> { facade.getEngine(provider.key) }
    }

    @Test
    fun shutdownAttemptsEveryEngineAndCanRetryFailedCleanup() = runTest {
        var fail = true
        val first = TestEngineProvider(TestEngineKey("first")) {
            TestEngine(it) { check(!fail) { "Cleanup failed" } }
        }
        val second = TestEngineProvider(TestEngineKey("second"))
        val facade = DefaultAiEngineFacade(setOf(first, second), AppCoroutineScope(backgroundScope))
        facade.getEngine(first.key)
        facade.getEngine(second.key)
        val firstEngine = assertNotNull(first.latestRuntime)
        val secondEngine = assertNotNull(second.latestRuntime)

        assertFailsWith<IllegalStateException> { facade.dispose() }
        assertEquals(1, secondEngine.disposals)
        assertFailsWith<IllegalStateException> { facade.getEngine(second.key) }

        fail = false
        facade.dispose()
        assertEquals(2, firstEngine.disposals)
        assertEquals(1, secondEngine.disposals)
        assertEquals(setOf(AiEngineState.NotInitialized), facade.engines.value.values.toSet())
    }

    private companion object {
        const val STATE_WINDOW = 500L
    }
}
