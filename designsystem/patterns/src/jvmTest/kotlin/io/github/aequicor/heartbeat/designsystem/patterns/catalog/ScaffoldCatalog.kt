@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.patterns.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbWorkspaceLayout
import io.github.aequicor.heartbeat.designsystem.patterns.HbWorkspaceScaffold
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Rendered workspace scaffolds at the documented widths (HB-DS-031, HB-DS-032). */
@Composable
internal fun ScaffoldCatalogScreen(modifier: Modifier = Modifier) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.canvas)
            .padding(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        ScaffoldSample(width = 320)
        ScaffoldSample(width = 800)
        ScaffoldSample(width = 1300)
    }
}

@Composable
private fun ScaffoldSample(
    width: Int,
    modifier: Modifier = Modifier,
) {
    val colors = HbThemeDefaults.LocalColors.current
    val fit = HbWorkspaceLayout.fit(width)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        BasicText(
            text = "$width · ${fit.navigation.name} · боковая ${fit.sidebar} · " +
                "инспектор ${fit.inspector} · вторая ${fit.secondPane}",
            style = HbTypography.Metadata,
            color = { colors.text.secondary },
        )
        Box(modifier = Modifier.width(width.dp).height(PANE_HEIGHT)) {
            HbWorkspaceScaffold(
                fit = fit,
                navigation = { Pane(label = "Рейка") },
                sidebar = { Pane(label = "Сессии") },
                content = { Pane(label = "Диалог") },
                secondPane = { Pane(label = "Просмотр") },
                inspector = { Pane(label = "Инспектор") },
            )
        }
    }
}

@Composable
private fun Pane(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.subtle)
            .padding(HbSpacing.Xs),
    ) {
        BasicText(
            text = label,
            style = HbTypography.Caption,
            color = { colors.text.secondary },
        )
    }
}

private val PANE_HEIGHT = 120.dp
