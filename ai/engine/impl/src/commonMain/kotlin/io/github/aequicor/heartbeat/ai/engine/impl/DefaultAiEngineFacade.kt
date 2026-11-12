package io.github.aequicor.heartbeat.ai.engine.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.ai.engine.api.AiEngine
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineFacade
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineProvider
import io.github.aequicor.heartbeat.ai.engine.api.EngineKey
import io.github.aequicor.heartbeat.ai.engine.api.EngineState
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn

/**
 * Default catalog and aggregator of AI engines.
 *
 * Providers arrive as a Metro set. The facade validates unique ids, publishes the whole catalog
 * with `NotInitialized` placeholders, creates engines on first use and disposes them on request.
 * [engines] is a throttled snapshot: the throttle always publishes the last state, so the UI ends
 * on the truth even after a burst of transitions.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class DefaultAiEngineFacade @Inject constructor(
    providers: Set<AiEngineProvider>,
    appCoroutineScope: AppCoroutineScope,
) : AiEngineFacade {

    private val providersById: Map<String, AiEngineProvider>

    init {
        val duplicates = providers.groupBy { it.key.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicate AI engine ids: ${duplicates.sorted().joinToString()}"
        }
        providersById = providers.associateBy { it.key.id }
    }

    private val catalog: List<EngineKey> = providersById.values.map { it.key }.sortedBy { it.id }

    private val catalogById: Map<String, EngineKey> = catalog.associateBy { it.id }

    private val created = MutableStateFlow<Map<EngineKey, AiEngine>>(mapOf())

    override val availableEngines: StateFlow<List<EngineKey>> =
        MutableStateFlow(catalog).asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override val engines: StateFlow<Map<EngineKey, EngineState>> = created
        .flatMapLatest { engines -> engineStates(engines) }
        .map { states -> catalog.associateWith { states[it] ?: EngineState.NotInitialized } }
        .sample(THROTTLE_MILLIS)
        .stateIn(
            scope = appCoroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = catalog.associateWith { EngineState.NotInitialized },
        )

    override fun getEngine(key: EngineKey): AiEngine {
        val catalogKey = catalogById[key.id] ?: throw IllegalArgumentException(
            "Unknown AI engine id: ${key.id}",
        )
        while (true) {
            val current = created.value
            val existing = current[catalogKey]
            if (existing != null) return existing
            val candidate = providersById.getValue(catalogKey.id).create()
            check(candidate.key.id == catalogKey.id) {
                "Provider ${catalogKey.id} created an engine with id ${candidate.key.id}"
            }
            // The candidate allocated nothing yet, so losing the race only drops the object.
            if (created.compareAndSet(current, current + (catalogKey to candidate))) {
                return candidate
            }
        }
    }

    override suspend fun dispose(key: EngineKey) {
        val catalogKey = catalogById[key.id] ?: return
        val engine = takeOut(catalogKey) ?: return
        engine.dispose()
    }

    /** Removes the engine from the runtime map; `null` when it was not created. */
    private fun takeOut(key: EngineKey): AiEngine? {
        while (true) {
            val current = created.value
            val engine = current[key] ?: return null
            if (created.compareAndSet(current, current - key)) return engine
        }
    }

    private fun engineStates(
        engines: Map<EngineKey, AiEngine>,
    ): Flow<Map<EngineKey, EngineState>> {
        if (engines.isEmpty()) return flowOf(mapOf())
        val states = engines.map { (key, engine) -> engine.state.map { key to it } }
        return combine(states) { values -> values.toMap() }
    }

    private companion object {
        private const val THROTTLE_MILLIS = 500L
    }
}
