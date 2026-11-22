package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingField
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State

private const val ChevronRotation = -90f

// Focus is requested once when the search dialog opens.
@Suppress("UnnecessaryLaunchedEffect")
@Composable
internal fun EngineSettingSearch(onSelect: (EngineSettingField) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) { EngineSettingField.search(query) }
    val focus = remember { FocusRequester() }
    SettingsModal(Layout.searchWidth, onDismiss) {
        Column(Modifier.fillMaxWidth().padding(Aurora.ContentInset)) {
            Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
                verticalAlignment = Alignment.CenterVertically) {
                SettingsInput(query, { query = it }, "Найти настройку", Modifier.weight(1f)
                    .focusRequester(focus).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.NumPadEnter)
                        && matches.isNotEmpty()) {
                        onSelect(matches.first())
                        true
                    } else false
                }, placeholder = "Найти настройку", leading = { HbIcon(HbIconGlyph.Search, size = Aurora.Glyph) })
                SettingsButton("", onDismiss, Modifier.width(Layout.fieldHeight),
                    variant = HbButtonVariant.Ghost, description = "Закрыть поиск") {
                    HbIcon(HbIconGlyph.Close, tint = it, size = Aurora.SmallGlyph)
                }
            }
            LaunchedEffect(focus) { focus.requestFocus() }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (matches.isEmpty()) {
                    SettingsText("Настройки не найдены", Modifier.padding(HbSpacing.S))
                    SettingsButton("Сбросить поиск", { query = ""; focus.requestFocus() })
                }
                matches.groupBy(EngineSettingField::group).entries.forEachIndexed { index, (group, fields) ->
                    if (index > 0) SettingsRule()
                    Spacer(Modifier.height(HbSpacing.S))
                    SettingsText("Движки / $group", Modifier.padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
                        HbTypography.Caption, HbTheme.colors.text.secondary)
                    fields.forEach { field ->
                        Row(Modifier.fillMaxWidth().heightIn(min = Layout.fieldHeight).clip(HbShapes.Small)
                            .clickable(role = Role.Button) { onSelect(field) }.padding(horizontal = HbSpacing.S),
                            verticalAlignment = Alignment.CenterVertically) {
                            SettingsText(field.label, Modifier.weight(1f))
                            HbIcon(HbIconGlyph.ChevronDown, Modifier.rotate(ChevronRotation), size = Aurora.SmallGlyph)
                        }
                    }
                }
            }
        }
    }
}

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
