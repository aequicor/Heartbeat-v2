package io.github.aequicor.heartbeat.features.aienginefacade.di

import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeEngine
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeProvider
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeSession
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeEngineConnection
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeEngineEvent
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeEngineKey
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeEngineState
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeSessionConfig
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeSessionKey
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeSessionPage
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeSessionSnapshot
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
import kotlin.test.assertNotEquals
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
        assertEquals(mapOf(provider.key to AiEngineFacadeEngineState.NotInitialized), facade.engines.value)
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

        engine.state.value = AiEngineFacadeEngineState.Connecting
        runCurrent()
        advanceTimeBy(STATE_WINDOW - 1)
        engine.state.value = AiEngineFacadeEngineState.Ready
        assertEquals(AiEngineFacadeEngineState.Ready, engine.state.value)
        assertEquals(AiEngineFacadeEngineState.NotInitialized, facade.engines.value[provider.key])

        advanceTimeBy(1)
        runCurrent()
        assertEquals(AiEngineFacadeEngineState.Ready, facade.engines.value[provider.key])

        facade.dispose(provider.key)
        engine.state.value = AiEngineFacadeEngineState.Failed("Stale instance")
        runCurrent()
        advanceTimeBy(STATE_WINDOW)
        runCurrent()
        assertEquals(AiEngineFacadeEngineState.NotInitialized, facade.engines.value[provider.key])
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
        assertEquals(setOf(AiEngineFacadeEngineState.NotInitialized), facade.engines.value.values.toSet())
    }

    @Test
    fun sessionIdentityIgnoresMetadataAndIncludesEngineId() {
        val original = AiEngineFacadeSessionKey(TestEngineKey("first"), "chat", title = "Before")
        val renamed = AiEngineFacadeSessionKey(TestEngineKey("first", "Renamed"), "chat", title = "After")
        val differentEngine = AiEngineFacadeSessionKey(TestEngineKey("second"), "chat", title = "Before")

        assertEquals(original, renamed)
        assertEquals(original.hashCode(), renamed.hashCode())
        assertNotEquals(original, differentEngine)
    }

    private companion object {
        const val STATE_WINDOW = 500L
    }
}

internal data class TestEngineKey(override val id: String, override val name: String = id) : AiEngineFacadeEngineKey {
    override val description: String = name
    override val connection: AiEngineFacadeEngineConnection = AiEngineFacadeEngineConnection.EMBEDDED
}

internal class TestEngineProvider(
    override val key: AiEngineFacadeEngineKey,
    private val createEngine: (AiEngineFacadeEngineKey) -> TestEngine = { TestEngine(it) },
) : AiEngineFacadeProvider {
    var creations: Int = 0
        private set

    override fun create(): AiEngineFacadeEngine {
        creations++
        return createEngine(key)
    }
}

internal class TestEngine(
    override val key: AiEngineFacadeEngineKey,
    private val onDispose: suspend () -> Unit = {},
) : AiEngineFacadeEngine {
    override val state = MutableStateFlow<AiEngineFacadeEngineState>(AiEngineFacadeEngineState.NotInitialized)
    override val sessions = MutableStateFlow<List<AiEngineFacadeSessionSnapshot>>(listOf())
    var disposals: Int = 0
        private set

    override fun dispatch(event: AiEngineFacadeEngineEvent) = Unit
    override suspend fun listSessions(cursor: String?): AiEngineFacadeSessionPage =
        AiEngineFacadeSessionPage(listOf())

    override suspend fun createSession(
        config: AiEngineFacadeSessionConfig,
        existing: AiEngineFacadeSessionKey?,
    ): AiEngineFacadeSession = error("Sessions are not used in facade tests")

    override fun getSession(key: AiEngineFacadeSessionKey): AiEngineFacadeSession? = null
    override suspend fun closeSession(key: AiEngineFacadeSessionKey) = Unit

    override suspend fun dispose() {
        disposals++
        onDispose()
    }
}
