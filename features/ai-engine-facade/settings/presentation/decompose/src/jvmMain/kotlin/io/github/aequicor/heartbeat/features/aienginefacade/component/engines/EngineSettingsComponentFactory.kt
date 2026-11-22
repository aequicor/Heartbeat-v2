package io.github.aequicor.heartbeat.features.aienginefacade.component.engines

import com.arkivanov.decompose.ComponentContext

public fun interface EngineSettingsComponentFactory {

    public fun create(context: ComponentContext, onClose: () -> Unit): EngineSettingsComponent
}
