package io.github.aequicor.heartbeat.features.aienginefacade.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade

@DependencyGraph(AppScope::class)
internal interface EngineGraph {
    val facade: AiEngineFacade
    val providers: Set<AiEngineProvider>
    val provider: MetroTestEngineProvider get() = providers.single() as MetroTestEngineProvider

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides scope: AppCoroutineScope): EngineGraph
    }
}
