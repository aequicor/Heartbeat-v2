@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.foundation.HbNavigationMode
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.foundation.HbWorkspaceFit
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults

/**
 * Workspace container that places the regions of [HbWorkspaceFit] (HB-DS-031, HB-DS-032).
 *
 * The scaffold renders only the regions the fit policy reports as visible, so the preferred
 * arrangement is restored when the width returns and no region is squeezed below its minimum. A
 * drawer navigation is not rendered inline: the owner opens it as an overlay in the compact class,
 * because an overlay changes the focus order and belongs to the surface that presents it.
 *
 * The order of regions is navigation, sidebar, focused content, optional second pane and optional
 * inspector, each separated by the one-unit separator of the geometry contract.
 */
@Composable
fun HbWorkspaceScaffold(
    fit: HbWorkspaceFit,
    modifier: Modifier = Modifier,
    sidebarWidth: Dp = HbPaneGeometry.SidebarPreferred.dp,
    inspectorWidth: Dp = HbPaneGeometry.InspectorPreferred.dp,
    navigation: @Composable () -> Unit = {},
    sidebar: @Composable () -> Unit = {},
    inspector: @Composable () -> Unit = {},
    secondPane: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Row(modifier = modifier.fillMaxSize().background(colors.surface.canvas)) {
        if (fit.navigation != HbNavigationMode.Drawer) {
            Box(modifier = Modifier.width(NAVIGATION_RAIL).fillMaxHeight()) {
                navigation()
            }
            HbDivider(vertical = true)
        }
        if (fit.sidebar) {
            Box(modifier = Modifier.width(sidebarWidth).fillMaxHeight()) {
                sidebar()
            }
            HbDivider(vertical = true)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
        if (fit.secondPane) {
            HbDivider(vertical = true)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                secondPane()
            }
        }
        if (fit.inspector) {
            HbDivider(vertical = true)
            Box(modifier = Modifier.width(inspectorWidth).fillMaxHeight()) {
                inspector()
            }
        }
    }
}

private val NAVIGATION_RAIL = HbPaneGeometry.NavigationRail.dp
