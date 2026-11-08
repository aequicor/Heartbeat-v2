package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.foundation.HbWindowHeight
import io.github.aequicor.heartbeat.designsystem.foundation.HbWindowWidth
import io.github.aequicor.heartbeat.designsystem.foundation.HbWorkspaceFit
import io.github.aequicor.heartbeat.designsystem.foundation.HbWorkspaceLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import kotlin.math.roundToInt

/**
 * Resolves the adaptive inputs of a layout (HB-DS-031).
 *
 * The available width and height come from the layout itself, not from a device category, so a
 * narrow desktop window and a wide tablet get the same treatment, and a short host gets the same
 * reduced-chrome signal as a tall one. The callback receives the width class, the height class and
 * the regions that fit; the owner keeps its preferred arrangement in the workspace state and
 * restores it when the width returns. [sidebar], [inspector] and [secondPane] are the preferred
 * visibility of those regions: a requested region stays closed when the owner keeps it closed, and
 * only a width that cannot fit it hides it.
 */
@Composable
fun HbAdaptive(
    modifier: Modifier = Modifier,
    sidebarWidth: Int = HbPaneGeometry.SidebarPreferred,
    inspectorWidth: Int = HbPaneGeometry.InspectorPreferred,
    secondPane: Boolean = false,
    inspector: Boolean = true,
    sidebar: Boolean = true,
    content: @Composable (windowWidth: HbWindowWidth, windowHeight: HbWindowHeight, fit: HbWorkspaceFit) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth.value.roundToInt()
        val availableHeight = maxHeight.value.roundToInt()
        content(
            HbWindowWidth.of(availableWidth),
            HbWindowHeight.of(availableHeight),
            HbWorkspaceLayout.fit(
                availableWidth = availableWidth,
                sidebarWidth = sidebarWidth,
                inspectorWidth = inspectorWidth,
                secondPane = secondPane,
                inspector = inspector,
                sidebar = sidebar,
                splitterWidth = HbSizes.minimumTarget.value.roundToInt(),
            ),
        )
    }
}
