package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.aequicor.heartbeat.designsystem.components.HbTabs
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

/**
 * Desktop sandbox of the design system (HB-DS-039).
 *
 * The entry point is a review tool, not a product screen: it renders the kit and the adaptive
 * classes in one window, so the owner can inspect states that a static screenshot cannot show. It
 * depends only on the published design-system modules.
 */
object HbSandboxEntry {

    @JvmStatic
    fun main(args: Array<String>) {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Heartbeat — каталог дизайн-системы",
                state = rememberWindowState(size = DpSize(1440.dp, 1000.dp)),
            ) {
                HbTheme(appearance = HbAppearance.System) {
                    SandboxHost()
                }
            }
        }
    }
}

@Composable
private fun SandboxHost(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(SandboxTab.Components) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(HbSpacing.M),
    ) {
        HbTabs(
            tabs = SandboxTab.entries,
            selected = selectedTab,
            onSelect = { selectedTab = it },
            tabLabel = { it.title },
        )
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                SandboxTab.Components -> SandboxScreen()
                SandboxTab.Adaptive -> AdaptiveSandboxScreen()
            }
        }
    }
}

private enum class SandboxTab(val title: String) {
    Components("Компоненты"),
    Adaptive("Адаптивность"),
}
