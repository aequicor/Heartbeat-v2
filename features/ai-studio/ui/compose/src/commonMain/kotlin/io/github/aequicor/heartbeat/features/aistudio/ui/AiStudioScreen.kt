package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.foundation.HbNavigationMode
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.foundation.HbWorkspaceLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioState
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AiStudioNavigationRail
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AiStudioSidebar
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AiStudioWorkspace

/** Renders the studio snapshot and forwards user actions to its store. */
@Composable
fun AiStudioScreen(
    state: AiStudioState,
    onAction: (AiStudioAction) -> Unit,
    avatarVisible: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    var showChats by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.canvas)
            .imePadding(),
    ) {
        val fit = HbWorkspaceLayout.fit(
            availableWidth = maxWidth.value.toInt(),
            inspector = false,
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // The overlay drawer is not implemented yet; keep the rail visible in compact mode so
            // the workspace never loses access to navigation and settings on narrow widths.
            if (fit.navigation == HbNavigationMode.Rail || fit.navigation == HbNavigationMode.Drawer) {
                AiStudioNavigationRail(
                    avatarVisible = avatarVisible,
                    onOpenSettings = onOpenSettings,
                )
                HbDivider(vertical = true)
            }
            if (fit.sidebar) {
                AiStudioSidebar(
                    state = state,
                    onAction = onAction,
                    onClose = null,
                    modifier = Modifier.width(HbPaneGeometry.SidebarPreferred.dp),
                )
                HbDivider(vertical = true)
            }
            if (!fit.sidebar && showChats && state.connected) {
                AiStudioSidebar(
                    state = state,
                    onAction = { action ->
                        if (action is AiStudioAction.OpenChat || action == AiStudioAction.NewChat) showChats = false
                        onAction(action)
                    },
                    onClose = { showChats = false },
                    modifier = Modifier.weight(1f),
                )
            } else {
                AiStudioWorkspace(
                    state = state,
                    onAction = onAction,
                    onShowChats = if (fit.sidebar) null else { { showChats = true } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
