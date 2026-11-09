package io.github.aequicor.heartbeat.ai.engine.impl

import io.github.aequicor.heartbeat.ai.engine.api.AiEngine
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineFacade
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineProvider
import io.github.aequicor.heartbeat.ai.engine.api.EngineCapabilities
import io.github.aequicor.heartbeat.ai.engine.api.EngineConnection
import io.github.aequicor.heartbeat.ai.engine.api.EngineEvent
import io.github.aequicor.heartbeat.ai.engine.api.EngineKey
import io.github.aequicor.heartbeat.ai.engine.api.EngineState
import io.github.aequicor.heartbeat.ai.engine.api.SessionConfig
import io.github.aequicor.heartbeat.ai.engine.api.SessionKey
import io.github.aequicor.heartbeat.ai.engine.api.SessionPage
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAiEngineFacadeTest {

    private fun key(
        id: String,
        name: String = id,
    ): EngineKey = EngineKey(id, name, "test engine", EngineConnection.ACP)

    private fun TestScope.facade(providers: List<AiEngineProvider>): AiEngineFacade =
        DefaultAiEngineFacade(providers.toSet(), AppCoroutineScope(backgroundScope))

    @Test
    fun duplicateEngineIdsAreRejected() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            facade(listOf(FakeProvider(key("acp.duplicate")), FakeProvider(key("acp.duplicate"))))
        }

        assertTrue(failure.message.orEmpty().contains("acp.duplicate"))
    }

    @Test
    fun catalogPublishesEveryEngine() = runTest {
        val facade = facade(listOf(FakeProvider(key("acp.b")), FakeProvider(key("acp.a"))))

        assertEquals(listOf("acp.a", "acp.b"), facade.availableEngines.value.map { it.id })
        assertEquals(
            mapOf(key("acp.a") to EngineState.NotInitialized, key("acp.b") to EngineState.NotInitialized),
            facade.engines.value,
        )
    }

    @Test
    fun engineCreationIsIdempotent() = runTest {
        val provider = FakeProvider(key("acp.a"))
        val facade = facade(listOf(provider))

        val first = facade.getEngine(key("acp.a"))
        val restored = facade.getEngine(key("acp.a", name = "Restored from the database"))

        assertSame(first, restored)
        assertEquals(1, provider.created)
    }

    @Test
    fun disposeReleasesTheEngineAndCreatesItAgain() = runTest {
        val provider = FakeProvider(key("acp.a"))
        val facade = facade(listOf(provider))
        val first = facade.getEngine(key("acp.a")) as FakeEngine

        facade.dispose(key("acp.a"))

        assertTrue(first.disposed)
        val second = facade.getEngine(key("acp.a"))
        assertNotSame(first, second)
        assertEquals(2, provider.created)
    }

    @Test
    fun unknownEngineIdIsRejected() = runTest {
        val facade = facade(listOf(FakeProvider(key("acp.a"))))

        assertFailsWith<IllegalArgumentException> { facade.getEngine(key("acp.unknown")) }
    }

    @Test
    fun statesAreThrottledAndTheLastStateWins() = runTest {
        val facade = facade(listOf(FakeProvider(key("acp.a"))))
        val engine = facade.getEngine(key("acp.a")) as FakeEngine
        val publishedAt = mutableListOf<Long>()

        backgroundScope.launch {
            facade.engines.collect { publishedAt += testScheduler.currentTime }
        }
        runCurrent()

        engine.state.value = EngineState.Connecting
        advanceTimeBy(100)
        engine.state.value = EngineState.Ready
        advanceTimeBy(100)
        engine.state.value = EngineState.Failed("boom")
        advanceTimeBy(600)
        runCurrent()

        assertEquals(EngineState.Failed("boom"), facade.engines.value[key("acp.a")])
        assertTrue(
            publishedAt.zipWithNext().all { (previous, next) -> next - previous >= 500 },
            "Publications are throttled: $publishedAt",
        )
    }

    @Test
    fun engineReturnsToNotInitializedAfterDispose() = runTest {
        val facade = facade(listOf(FakeProvider(key("acp.a"))))
        facade.getEngine(key("acp.a"))
        advanceTimeBy(600)
        runCurrent()

        facade.dispose(key("acp.a"))
        advanceTimeBy(600)
        runCurrent()

        assertEquals(EngineState.NotInitialized, facade.engines.value[key("acp.a")])
    }
}

private class FakeProvider(
    override val key: EngineKey,
) : AiEngineProvider {

    var created: Int = 0
        private set

    override fun create(): AiEngine {
        created += 1
        return FakeEngine(key)
    }
}

private class FakeEngine(
    override val key: EngineKey,
) : AiEngine {

    override val state = MutableStateFlow<EngineState>(EngineState.NotInitialized)

    override val capabilities: StateFlow<EngineCapabilities> =
        MutableStateFlow(EngineCapabilities())

    override val sessions: StateFlow<List<SessionKey>> = MutableStateFlow(listOf())

    var disposed: Boolean = false
        private set

    override fun dispatch(event: EngineEvent) {
        when (event) {
            EngineEvent.Connect -> state.value = EngineState.Ready
            EngineEvent.Disconnect -> state.value = EngineState.NotInitialized
            is EngineEvent.AuthorizationCompleted -> Unit
        }
    }

    override suspend fun listSessions(cursor: String?): SessionPage = SessionPage(listOf())

    override suspend fun createSession(
        config: SessionConfig,
        existing: SessionKey?,
    ): io.github.aequicor.heartbeat.ai.engine.api.AiSession = error("not used")

    override fun getSession(key: SessionKey): io.github.aequicor.heartbeat.ai.engine.api.AiSession? = null

    override suspend fun closeSession(key: SessionKey) = Unit

    override suspend fun dispose() {
        disposed = true
    }
}
