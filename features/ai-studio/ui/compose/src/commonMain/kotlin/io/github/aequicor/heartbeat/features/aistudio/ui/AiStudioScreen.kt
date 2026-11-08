package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.foundation.HbNavigationMode
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.foundation.HbWorkspaceLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AiStudioNavigationRail
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AiStudioSidebar
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AiStudioWorkspace

/**
 * AI studio workspace: navigation rail, conversation sidebar and the focused chat.
 *
 * The screen renders the agreed layout with sample content and owns no feature logic. Navigation,
 * search and composer actions are visual placeholders; a store and a presentation adapter will
 * supply them later without changing the structure. [avatarVisible] and [onOpenSettings] come from
 * the AI studio preferences and the feature root respectively.
 */
@Composable
fun AiStudioScreen(
    avatarVisible: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

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
                    modifier = Modifier.width(HbPaneGeometry.SidebarPreferred.dp),
                )
                HbDivider(vertical = true)
            }
            AiStudioWorkspace(modifier = Modifier.weight(1f))
        }
    }
}
