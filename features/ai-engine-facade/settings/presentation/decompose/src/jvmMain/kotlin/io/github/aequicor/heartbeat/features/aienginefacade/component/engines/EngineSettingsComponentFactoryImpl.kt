package io.github.aequicor.heartbeat.features.aienginefacade.component.engines

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.getOrCreate
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsStoreFactory
import kotlinx.coroutines.CoroutineDispatcher

@ContributesBinding(AppScope::class)
public class EngineSettingsComponentFactoryImpl(
    private val factory: EngineSettingsStoreFactory,
    @Named("engine-settings-ui") private val dispatcher: CoroutineDispatcher,
) : EngineSettingsComponentFactory {

    override fun getOrCreate(context: ComponentContext): EngineSettingsComponent =
        context.instanceKeeper.getOrCreate { EngineSettingsComponentImpl(factory, dispatcher) }
}
