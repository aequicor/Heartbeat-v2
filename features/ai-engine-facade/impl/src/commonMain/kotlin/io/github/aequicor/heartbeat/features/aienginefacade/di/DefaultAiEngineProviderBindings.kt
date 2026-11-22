package io.github.aequicor.heartbeat.features.aienginefacade.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import kotlinx.coroutines.flow.Flow

@BindingContainer
@ContributesTo(AppScope::class)
interface DefaultAiEngineProviderBindings {
    @Multibinds(allowEmpty = true)
    fun aiEngineProviders(): Set<AiEngineProvider>

    @Multibinds(allowEmpty = true)
    fun aiEngineCatalogs(): Set<Flow<List<AiEngineProvider>>>
}
