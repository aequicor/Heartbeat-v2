package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingField
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State

@Composable
internal fun SettingGroup(
    field: EngineSettingField,
    state: State.Loaded,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val active = field == state.highlighted
    val requester = remember { BringIntoViewRequester() }
    Column(modifier.bringIntoViewRequester(requester), verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
        SettingsText(field.label,
            if (active) Modifier.background(HbTheme.colors.accent.container, HbShapes.Small)
                .padding(horizontal = HbSpacing.Xs) else Modifier,
            HbTypography.PaneTitle)
        content()
    }
    LaunchedEffect(active, state.highlightRequest) { if (active) requester.bringIntoView() }
}

internal fun Modifier.dismissOnEscape(onDismiss: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
        onDismiss()
        true
    } else false
}
