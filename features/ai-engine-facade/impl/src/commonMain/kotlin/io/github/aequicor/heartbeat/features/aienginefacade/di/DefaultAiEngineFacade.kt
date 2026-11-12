package io.github.aequicor.heartbeat.features.aienginefacade.di

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngine
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAiEngineFacade(
    providers: Set<AiEngineProvider>,
    private val appScope: AppCoroutineScope,
) : AiEngineFacade {

    private val providersById = buildMap {
        for (provider in providers) {
            val id = provider.key.id
            require(put(id, provider) == null) { "Duplicate AI engine ID: $id" }
        }
    }
    private val keys = providersById.values.map(AiEngineProvider::key)
    private val initialStates: Map<AiEngineKey, AiEngineState> =
        keys.associateWith { AiEngineState.NotInitialized }
    private val engineStates = MutableStateFlow(initialStates)

    override val availableEngines: StateFlow<List<AiEngineKey>> = MutableStateFlow(keys).asStateFlow()
    override val engines: StateFlow<Map<AiEngineKey, AiEngineState>> =
        engineStates.asStateFlow()

    private val registryLock = reentrantLock()
    private val disposalLock = Mutex()
    private val active = mutableMapOf<String, ActiveEngine>()
    private var closed = false

    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val publisher = appScope.launch {
        while (changes.receiveCatching().isSuccess) {
            delay(STATE_WINDOW_MILLIS)
            changes.tryReceive()
            // Read current states so a removed instance cannot publish a stale update.
            engineStates.value = registryLock.withLock {
                keys.associateWith { active[it.id]?.state?.value ?: AiEngineState.NotInitialized }
            }
        }
    }

    override fun getEngine(key: AiEngineKey): AiEngine = registryLock.withLock {
        check(!closed) { "AI engine facade is closed" }
        val id = key.id
        val provider = requireNotNull(providersById[id]) { "Unknown AI engine ID: $id" }
        val entry = active.getOrPut(id) {
            val engine = provider.create()
            val observer = appScope.launch { engine.state.collect { changes.trySend(Unit) } }
            changes.trySend(Unit)
            ActiveEngine(id, engine, observer)
        }
        check(!entry.disposing) { "AI engine $id is pending disposal" }
        entry
    }

    override suspend fun dispose() {
        disposalLock.withLock {
            withContext(NonCancellable) {
                val entries = registryLock.withLock {
                    closed = true
                    active.values.toList()
                }
                val failures = entries.mapNotNull { entry -> runCatching { entry.release() }.exceptionOrNull() }
                failures.firstOrNull()?.let { first ->
                    failures.drop(1).filter { it !== first }.forEach(first::addSuppressed)
                    throw first
                }
                publisher.cancelAndJoin()
                changes.close()
                engineStates.value = initialStates
            }
        }
    }

    private inner class ActiveEngine(
        private val id: String,
        private val runtime: AiEngine,
        private val observer: Job,
    ) : AiEngine by runtime {
        var disposing = false

        override suspend fun dispose() {
            disposalLock.withLock {
                withContext(NonCancellable) { release() }
            }
        }

        suspend fun release() {
            registryLock.withLock {
                if (active[id] !== this) return
                disposing = true
            }
            runtime.dispose()
            observer.cancel()
            registryLock.withLock { active.remove(id) }
            changes.trySend(Unit)
        }
    }

    private companion object {
        const val STATE_WINDOW_MILLIS = 500L
    }
}
