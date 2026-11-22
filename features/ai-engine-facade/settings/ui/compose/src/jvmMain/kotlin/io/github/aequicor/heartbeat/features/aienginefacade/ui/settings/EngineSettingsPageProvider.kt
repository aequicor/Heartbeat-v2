package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aienginefacade.component.engines.EngineSettingsComponentFactory
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider
import kotlinx.coroutines.launch

@ContributesIntoSet(AppScope::class)
public class EngineSettingsPageProvider(
    private val factory: EngineSettingsComponentFactory,
) : ComposeSettingsPageProvider {

    override val pageIds = setOf(EngineSettingsPageId)
    override val providesNavigation = true

    @Composable
    override fun Content(pageId: String, context: ComponentContext, onBack: () -> Unit) {
        val component = remember(factory, context) { factory.create(context, onBack) }
        val state by component.state.collectAsState()
        val scope = rememberCoroutineScope()
        EngineSettingsContent(state, localHost = true, onAction = { action ->
            scope.launch { component.dispatch(action) }
        })
    }
}
