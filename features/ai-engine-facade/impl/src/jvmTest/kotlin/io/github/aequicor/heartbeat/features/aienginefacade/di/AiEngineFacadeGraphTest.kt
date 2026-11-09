package io.github.aequicor.heartbeat.features.aienginefacade.di

import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeEngine
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeProvider
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacadeEngineKey
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AiEngineFacadeGraphTest {

    @Test
    fun metroCreatesTheFacadeWithoutProviders() = runTest {
        val graph = createGraphFactory<EmptyEngineGraph.Factory>().create(AppCoroutineScope(backgroundScope))
        assertTrue(graph.facade.availableEngines.value.isEmpty())
        assertTrue(graph.facade.engines.value.isEmpty())
        graph.facade.dispose()
    }

    @Test
    fun metroCollectsProvidersAndConcurrentAccessCreatesOneEngine() = runTest {
        val graph = createGraphFactory<EngineGraph.Factory>().create(AppCoroutineScope(backgroundScope))
        val facade = graph.facade
        assertSame(facade, graph.facade)
        val key = facade.availableEngines.value.single()
        assertEquals("metro-test", key.id)
        assertEquals(0, graph.provider.creations)

        val engines = Executors.newFixedThreadPool(LOOKUP_THREADS).asCoroutineDispatcher().use { dispatcher ->
            val start = CompletableDeferred<Unit>()
            val lookups = List(CONCURRENT_LOOKUPS) {
                async(dispatcher) {
                    start.await()
                    facade.getEngine(key)
                }
            }
            start.complete(Unit)
            lookups.awaitAll()
        }

        engines.forEach { assertSame(engines.first(), it) }
        assertEquals(1, graph.provider.creations)
        facade.dispose()
    }

    private companion object {
        const val CONCURRENT_LOOKUPS = 16
        const val LOOKUP_THREADS = 2
    }
}

@ContributesIntoSet(AppScope::class)
@SingleIn(AppScope::class)
internal class MetroTestEngineProvider : AiEngineFacadeProvider {
    private val delegate = TestEngineProvider(TestEngineKey("metro-test"))
    override val key: AiEngineFacadeEngineKey = delegate.key
    val creations: Int get() = delegate.creations

    override fun create(): AiEngineFacadeEngine = delegate.create()
}

@DependencyGraph(AppScope::class, excludes = [MetroTestEngineProvider::class])
internal interface EmptyEngineGraph {
    val facade: AiEngineFacade

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides scope: AppCoroutineScope): EmptyEngineGraph
    }
}

@DependencyGraph(AppScope::class)
internal interface EngineGraph {
    val facade: AiEngineFacade
    val providers: Set<AiEngineFacadeProvider>
    val provider: MetroTestEngineProvider get() = providers.single() as MetroTestEngineProvider

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides scope: AppCoroutineScope): EngineGraph
    }
}
