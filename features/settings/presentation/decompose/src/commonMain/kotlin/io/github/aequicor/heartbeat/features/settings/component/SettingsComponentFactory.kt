package io.github.aequicor.heartbeat.features.settings.component

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.settings.api.SettingsRegistry

/**
 * Creates settings components for the shell. [ComponentContext] and the requested page are runtime
 * parameters, so they stay outside the Metro graph; the [SettingsRegistry] arrives as a binding.
 */
interface SettingsComponentFactory {
    fun create(
        componentContext: ComponentContext,
        requestedPageId: String?,
    ): SettingsComponent
}

@ContributesBinding(AppScope::class)
class DefaultSettingsComponentFactory(
    private val registry: SettingsRegistry,
) : SettingsComponentFactory {

    override fun create(
        componentContext: ComponentContext,
        requestedPageId: String?,
    ): SettingsComponent =
        DefaultSettingsComponent(componentContext, requestedPageId, registry)
}
