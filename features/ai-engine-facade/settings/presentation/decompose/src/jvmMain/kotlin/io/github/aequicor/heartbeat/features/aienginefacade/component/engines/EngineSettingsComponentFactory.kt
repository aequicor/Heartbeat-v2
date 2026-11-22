package io.github.aequicor.heartbeat.features.aienginefacade.component.engines

import com.arkivanov.decompose.ComponentContext

public fun interface EngineSettingsComponentFactory {

    public fun getOrCreate(context: ComponentContext): EngineSettingsComponent
}
