package io.github.aequicor.heartbeat.features.aienginefacade.di

import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineEvent
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSession
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSessionConfig
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSessionPage
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineSessionSnapshot
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val engine = facade.getEngine(provider.key) as TestEngine

        engine.state.value = AiEngineState.Connecting
        runCurrent()
        advanceTimeBy(STATE_WINDOW - 1)
        engine.state.value = AiEngineState.Ready
        assertEquals(AiEngineState.Ready, engine.state.value)
        assertEquals(AiEngineState.NotInitialized, facade.engines.value[provider.key])

        advanceTimeBy(1)
        runCurrent()
        assertEquals(AiEngineState.Ready, facade.engines.value[provider.key])

        facade.dispose(provider.key)
        engine.state.value = AiEngineState.Failed("Stale instance")
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
        val original = facade.getEngine(provider.key) as TestEngine
        val disposing = launch { facade.dispose(provider.key) }
        runCurrent()

        assertFailsWith<IllegalStateException> { facade.getEngine(provider.key) }
        canDispose.complete(Unit)
        disposing.join()
        facade.dispose(provider.key)

        val replacement = facade.getEngine(provider.key) as TestEngine
        assertNotSame(original, replacement)
        assertEquals(1, original.disposals)
        facade.dispose()
        assertEquals(1, replacement.disposals)
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
        val firstEngine = facade.getEngine(first.key) as TestEngine
        val secondEngine = facade.getEngine(second.key) as TestEngine

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

internal data class TestEngineKey(override val id: String, override val name: String = id) : AiEngineKey {
    override val description: String = name
    override val connection: AiEngineConnection = AiEngineConnection.EMBEDDED
}

internal class TestEngineProvider(
    override val key: AiEngineKey,
    private val createEngine: (AiEngineKey) -> TestEngine = { TestEngine(it) },
) : AiEngineProvider {
    var creations: Int = 0
        private set

    override fun create(): AiEngine {
        creations++
        return createEngine(key)
    }
}

internal class TestEngine(
    override val key: AiEngineKey,
    private val onDispose: suspend () -> Unit = {},
) : AiEngine {
    override val state = MutableStateFlow<AiEngineState>(AiEngineState.NotInitialized)
    override val sessions = MutableStateFlow<List<AiEngineSessionSnapshot>>(listOf())
    var disposals: Int = 0
        private set

    override fun dispatch(event: AiEngineEvent) = Unit
    override suspend fun listSessions(cursor: String?): AiEngineSessionPage =
        AiEngineSessionPage(listOf())

    override suspend fun createSession(
        config: AiEngineSessionConfig,
        existing: AiEngineSessionKey?,
    ): AiEngineSession = error("Sessions are not used in facade tests")

    override fun getSession(key: AiEngineSessionKey): AiEngineSession? = null
    override suspend fun closeSession(key: AiEngineSessionKey) = Unit

    override suspend fun dispose() {
        disposals++
        onDispose()
    }
}
