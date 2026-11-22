package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.ContributesIntoSet
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aienginefacade.component.engines.EngineSettingsComponentFactory
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingField
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.Res
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_cpu
import io.github.aequicor.heartbeat.navigation.compose.ComposeSettingsPageProvider
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@ContributesIntoSet(AppScope::class)
public class EngineSettingsPageProvider(
    private val factory: EngineSettingsComponentFactory,
) : ComposeSettingsPageProvider {

    override val pageIds = setOf(EngineSettingsPageId)

    override val navigationIcon: @Composable (String, Color) -> Unit = { _, tint ->
        SettingsIcon(Res.drawable.ic_settings_cpu, tint)
    }

    override fun search(pageId: String, query: String): List<ComposeSettingsPageProvider.SearchResult> =
        EngineSettingField.search(query).map { field ->
            ComposeSettingsPageProvider.SearchResult(field.label, field.group) { context ->
                val component = factory.getOrCreate(context)
                component.state.first { it != EngineSettingsScreenState.Loading }
                component.dispatch(EngineSettingsScreenAction.FindSetting(field))
            }
        }

    @Throwable(CancellationException::class)
    override suspend fun requestLeave(pageId: String, context: ComponentContext, leave: () -> Unit) {
        factory.getOrCreate(context).dispatch(EngineSettingsScreenAction.LeavePage(leave))
    }

    @Composable
    override fun Content(pageId: String, context: ComponentContext) {
        val component = remember(factory, context) { factory.getOrCreate(context) }
        val state by component.state.collectAsState()
        val scope = rememberCoroutineScope()
        EngineSettingsContent(state, localHost = true, onAction = { action ->
            scope.launch { component.dispatch(action) }
        })
    }
}
