package io.github.aequicor.heartbeat.features.aienginefacade.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppCoroutineScope
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aienginefacade.api.AiEngineFacade

@DependencyGraph(AppScope::class, excludes = [MetroTestEngineProvider::class])
internal interface EmptyEngineGraph {
    val facade: AiEngineFacade

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides scope: AppCoroutineScope): EmptyEngineGraph
    }
}
