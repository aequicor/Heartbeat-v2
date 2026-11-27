package io.github.aequicor.heartbeat.shell.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.navigation.compose.ComposeFeatureRenderer
import io.github.aequicor.heartbeat.navigation.compose.ComposeWindowOverlayHost
import io.github.aequicor.heartbeat.shell.ShellComponent
import kotlinx.coroutines.launch

/**
 * Renders the shell navigation: the active route is resolved to a feature renderer by `featureId`.
 * The screen never lists features, so a new feature does not change it.
 */
@Composable
fun ShellScreen(
    component: ShellComponent,
    renderers: Set<ComposeFeatureRenderer>,
    modifier: Modifier = Modifier,
) {
    val registry = remember(renderers) { RendererRegistry(renderers) }

    val stack = component.childStack.subscribeAsState()
    val scope = rememberCoroutineScope()
    ComposeWindowOverlayHost(modifier) {
        Row(Modifier.fillMaxSize()) {
            WorkspaceRail(component.workspaces, stack.value.active.configuration.featureId) { route ->
                val current = stack.value.active
                if (current.configuration.featureId != route.featureId) {
                    val renderer = registry.find(current.configuration.featureId)
                    if (renderer == null) component.open(route)
                    else scope.launch {
                        renderer.requestLeave(current.instance) {
                            if (component.childStack.value.active.instance === current.instance) component.open(route)
                        }
                    }
                }
            }
            Children(
                stack = component.childStack,
                modifier = Modifier.weight(1f),
            ) { child ->
                val renderer = registry.find(child.configuration.featureId)
                if (renderer == null) {
                    MissingFeatureFallback(child.configuration.featureId)
                } else {
                    renderer.Render(child.instance)
                }
            }
        }
    }
}

/** Keeps the shell usable when a restored route names a feature that is no longer available. */
@Composable
private fun MissingFeatureFallback(featureId: String) {
    HbText(
        text = if (featureId.isEmpty()) "Нет доступных рабочих областей" else "Раздел недоступен: $featureId",
        modifier = Modifier.fillMaxSize(),
        style = HbTypography.Detail,
    )
}

private class RendererRegistry(renderers: Set<ComposeFeatureRenderer>) {

    private val byId: Map<String, ComposeFeatureRenderer> = renderers.associateBy { it.featureId }

    init {
        require(byId.size == renderers.size) {
            "Duplicate feature renderers: " +
                renderers.groupBy { it.featureId }.filterValues { it.size > 1 }.keys
        }
    }

    fun find(featureId: String): ComposeFeatureRenderer? = byId[featureId]
}
