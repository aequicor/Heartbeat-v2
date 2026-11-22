package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbAuroraFieldSelect
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.AcpCheckState
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.AcpConnectionForm
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.AcpConnectionFormChange
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingField
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction as Action
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_folder
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_plug
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.Res

@Composable
internal fun AcpConnectionEditor(editor: State.Editor.Connection, state: State.Loaded, localHost: Boolean,
    onAction: (Action) -> Unit) {
    val form = editor.form
    val edit = { change: AcpConnectionFormChange -> onAction(Action.EditConnection(change)) }
    var manualSelected by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxHeight()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement
            .spacedBy(Layout.fieldGroupGap)) {
            SettingGroup(EngineSettingField.Preset, state) {
                val blankForm = AcpConnectionForm(ignoreEnvironmentNameCase = form.ignoreEnvironmentNameCase)
                val showPlaceholder = editor.engineId == null && !manualSelected && form == blankForm
                HbAuroraFieldSelect(AcpConnectionForm.Preset.entries, form.preset.takeUnless { showPlaceholder }, {
                    manualSelected = true
                    edit(AcpConnectionFormChange.PresetSelected(it))
                }, label = EngineSettingField.Preset.label, modifier = Modifier.fillMaxWidth(),
                    placeholder = "Выберите пресет", optionLabel = { it.label })
            }
            SettingGroup(EngineSettingField.ConnectionName, state) {
                SettingsInput(form.name, { edit(AcpConnectionFormChange.NameChanged(it)) },
                    EngineSettingField.ConnectionName.label)
            }
            CommandField(form.command, localHost, state, edit)
            ArgumentFields(form, state, edit)
            AcpEnvironmentFields(form, state, edit)
            Spacer(Modifier.height(HbSpacing.S))
        }
        ConnectionFooter(editor, state, onAction)
    }
}

// Footer elements are siblings in the editor column to preserve its layout.
@Suppress("MultipleEmitters")
@Composable
private fun ConnectionFooter(editor: State.Editor.Connection, state: State.Loaded, onAction: (Action) -> Unit) {
    val check = editor.check
    val failure = (check as? AcpCheckState.Failed)?.failure
    if (failure != null) SettingsText(failure.message, Modifier.padding(vertical = HbSpacing.M),
        HbTypography.Caption, HbTheme.colors.status.error.foreground)
    if (check is AcpCheckState.Connected) SettingsText("Связь установлена · ACP ${check.version}", Modifier
        .padding(vertical = HbSpacing.S),
        HbTypography.Caption, HbTheme.colors.status.success.foreground)
    val notice = state.notice
    if (notice != null) SettingsText(notice, Modifier.padding(vertical = HbSpacing.S), HbTypography.Caption)
    SettingsRule()
    ConnectionActions(editor, state, onAction)
}

@Composable
private fun ConnectionActions(editor: State.Editor.Connection, state: State.Loaded, onAction: (Action) -> Unit) {
    val form = editor.form
    val check = editor.check
    val failure = (check as? AcpCheckState.Failed)?.failure
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Aurora.ChromeInset)) {
        val narrow = maxWidth < Layout.checkWidth + Layout.saveWidth + HbSpacing.S
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            if (failure != null) {
                EngineErrorActions(failure) { onAction(Action.Check) }
            } else {
                val text = when (check) {
                    AcpCheckState.Checking -> "Проверяем связь…"
                    AcpCheckState.Retry -> "Повторить"
                    else -> "Проверить связь"
                }
                SettingsButton(
                    text,
                    { onAction(Action.Check) },
                    if (narrow) Modifier.weight(1f) else Modifier.width(Layout.checkWidth),
                    enabled = form.canCheck && check != AcpCheckState.Checking,
                    height = Layout.actionHeight,
                    startAligned = true,
                ) {
                    SettingsIcon(Res.drawable.ic_settings_plug, it)
                }
            }
            if (!narrow || failure != null) Spacer(Modifier.weight(1f))
            SettingsButton(
                "Сохранить",
                { onAction(Action.Save) },
                if (narrow) Modifier else Modifier.width(Layout.saveWidth),
                variant = HbButtonVariant.Primary,
                enabled = form.canSave && editor.changed && !state.saving,
                height = Layout.actionHeight,
            )
        }
    }
}

@Composable
private fun CommandField(command: String, localHost: Boolean, state: State.Loaded,
    onEdit: (AcpConnectionFormChange) -> Unit) {
    var pickerFailed by remember { mutableStateOf(false) }
    val pick = if (localHost) rememberCommandFilePicker(
        onSelect = { onEdit(AcpConnectionFormChange.CommandChanged(it)) },
        onError = { pickerFailed = true },
    ) else null
    SettingGroup(EngineSettingField.Command, state, Modifier.padding(bottom = HbSpacing.Xxs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Aurora.ContentInset)) {
            SettingsInput(command, { onEdit(AcpConnectionFormChange.CommandChanged(it)) },
                EngineSettingField.Command.label, Modifier.weight(1f))
            if (pick != null) SettingsButton("Выбрать файл…", {
                pickerFailed = false
                pick()
            }, Modifier.width(Layout.browseWidth), startAligned = true,
                horizontalInset = HbSpacing.S) { SettingsIcon(Res.drawable.ic_settings_folder, it) }
        }
        SettingsText("Введите CLI-команду из PATH, например dsh, или полный путь к программе.",
            style = HbTypography.Caption, color = HbTheme.colors.text.muted)
        if (pickerFailed) SettingsText("Не удалось открыть выбор файла", color = HbTheme.colors.status.error.foreground)
    }
}

@Composable
private fun ArgumentFields(form: AcpConnectionForm, state: State.Loaded, onEdit: (AcpConnectionFormChange) -> Unit) {
    val error = when {
        form.arguments() == null -> "Закройте двойные кавычки"
        '\u0000' in form.argumentText -> "Недопустимый символ"
        else -> null
    }
    Column(Modifier.padding(bottom = HbSpacing.Xxs), verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Aurora.ChromeInset)) {
            SettingGroup(EngineSettingField.Arguments, state, Modifier.weight(1f)) {
                SettingsInput(form.argumentText, { onEdit(AcpConnectionFormChange.ArgumentsChanged(it)) },
                    EngineSettingField.Arguments.label, errorMessage = error)
            }
            SettingGroup(EngineSettingField.Separator, state, Modifier.width(Layout.separatorWidth)) {
                HbAuroraFieldSelect(AcpConnectionForm.Separator.entries, form.separator,
                    { onEdit(AcpConnectionFormChange.SeparatorSelected(it)) },
                    label = EngineSettingField.Separator.label, optionLabel = { it.label })
            }
        }
        SettingsText(error ?: "Если аргумент содержит разделитель, заключите его в двойные кавычки.",
            style = HbTypography.Caption, color = if (error == null) HbTheme.colors.text.muted else HbTheme
                .colors.status.error.foreground)
    }
}
