package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingField
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction as Action
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_plug
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.Res

private const val ConnectionDetailsWeight = 2.5f

// Reset editing only when the save result changes.
@Suppress("UnnecessaryLaunchedEffect")
@Composable
internal fun ColumnScope.LocalEngineEditor(editor: State.Editor.Engine, state: State.Loaded,
    onAction: (Action) -> Unit) {
    var editing by remember(editor.engineId) { mutableStateOf(editor.changed) }
    LaunchedEffect(state.notice, editor.changed) {
        if (!editor.changed && state.notice == "Сохранено") editing = false
    }
    Box(Modifier.heightIn(min = Layout.fieldHeight), contentAlignment = Alignment.CenterStart) {
        SettingsText("Настройки движка", style = HbTypography.PageTitle)
    }
    Spacer(Modifier.height(Layout.headingGap))
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        SettingGroup(EngineSettingField.EngineName, state) {
            SettingsText(editor.name, style = HbTypography.Title)
        }
        Spacer(Modifier.height(HbSpacing.Xl))
        Column(Modifier.fillMaxWidth().clip(HbShapes.Medium).background(HbTheme.colors.surface.subtle)
            .clickable(role = Role.Button) { onAction(Action.OpenConnection) }.padding(HbSpacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
                verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(Res.drawable.ic_settings_plug, HbTheme.colors.text.secondary, Aurora.RequestGlyph)
                SettingsText("Общее ACP-подключение", style = HbTypography.PaneTitle)
            }
            Spacer(Modifier.height(HbSpacing.S))
            Row(Modifier.padding(start = Layout.headingGap), horizontalArrangement = Arrangement
                .spacedBy(HbSpacing.L)) {
                Column(Modifier.weight(1f)) {
                    SettingsText(editor.connectionName)
                    SettingsText(editor.command, style = HbTypography.Metadata, color = HbTheme.colors.text.secondary)
                }
                SettingsText("Команда, аргументы и среда общие с исходным движком.",
                    Modifier.weight(ConnectionDetailsWeight),
                    HbTypography.Caption, HbTheme.colors.text.secondary)
            }
        }
        Spacer(Modifier.height(HbSpacing.Xl))
        SettingGroup(EngineSettingField.ResponseFormat, state) {
            if (editing) SettingsInput(editor.responseFormat, { onAction(Action.SetResponseFormat(it)) },
                EngineSettingField.ResponseFormat.label)
            else SettingsText(editor.responseFormat.ifBlank { "По умолчанию" })
        }
        Spacer(Modifier.height(Layout.editorInset))
        SettingGroup(EngineSettingField.Rules, state) {
            if (editing) SettingsInput(editor.rules, { onAction(Action.SetRules(it)) },
                EngineSettingField.Rules.label, singleLine = false)
            else SettingsText(editor.rules.ifBlank { "Не заданы" })
        }
        Spacer(Modifier.height(HbSpacing.M))
    }
    val notice = state.notice
    if (notice != null) {
        SettingsText(notice, Modifier.padding(vertical = HbSpacing.S), HbTypography.Caption)
    }
    SettingsRule()
    Row(Modifier.fillMaxWidth().padding(top = Aurora.ChromeInset), horizontalArrangement = Arrangement.End) {
        SettingsButton(if (editing) "Сохранить" else "Изменить", {
            if (editing) onAction(Action.Save) else editing = true
        }, Modifier.width(Layout.saveWidth), variant = HbButtonVariant.Primary,
            enabled = !state.saving && (!editing || editor.changed), height = Layout.actionHeight)
    }
}
