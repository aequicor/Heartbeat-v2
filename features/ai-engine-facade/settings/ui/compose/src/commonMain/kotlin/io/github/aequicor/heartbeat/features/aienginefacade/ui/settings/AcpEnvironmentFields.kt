package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.AcpConnectionForm
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.AcpConnectionFormChange
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingField
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State

private const val NameWeight = 0.37f
private const val ValueWeight = 0.63f

// The focus request runs once when the requested row appears.
@Suppress("UnnecessaryLaunchedEffect")
@Composable
internal fun AcpEnvironmentFields(form: AcpConnectionForm, state: State.Loaded,
    onEdit: (AcpConnectionFormChange) -> Unit) {
    var requestedRow by remember { mutableStateOf<Long?>(null) }
    SettingGroup(EngineSettingField.Environment, state) {
        SettingsText("Эти переменные передаются процессу ACP-агента при запуске.",
            style = HbTypography.Caption, color = HbTheme.colors.text.muted)
        Row(Modifier.fillMaxWidth().padding(top = HbSpacing.Xs).background(HbTheme.colors.surface.subtle,
            RoundedCornerShape(HbSpacing.Xs))
            .heightIn(min = Aurora.NavigationRow), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
            SettingsText("Параметр", Modifier.weight(NameWeight).padding(start = Aurora.ContentInset),
                HbTypography.Caption, HbTheme.colors.text.secondary)
            SettingsText("Значение", Modifier.weight(ValueWeight).padding(start = Aurora.ContentInset),
                HbTypography.Caption, HbTheme.colors.text.secondary)
            Spacer(Modifier.width(Layout.fieldHeight))
        }
        form.environment.forEach { row ->
            key(row.rowId) {
                val focus = remember { FocusRequester() }
                val error = form.variableError(row)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
                    SettingsInput(row.name, { onEdit(AcpConnectionFormChange.VariableNameChanged(row.rowId, it)) },
                        "Параметр", Modifier.weight(NameWeight).focusRequester(focus), errorMessage = error)
                    SettingsInput(row.value, { onEdit(AcpConnectionFormChange.VariableValueChanged(row.rowId, it)) },
                        "Значение", Modifier.weight(ValueWeight))
                    SettingsButton("", { onEdit(AcpConnectionFormChange.VariableRemoved(row.rowId)) },
                        Modifier.width(Layout.fieldHeight),
                        variant = HbButtonVariant.Ghost, description = "Удалить переменную") {
                        HbIcon(HbIconGlyph.Close, tint = HbTheme.colors.text.muted, size = Aurora.SmallGlyph)
                    }
                }
                if (error != null) SettingsText(error, style = HbTypography.Caption, color = HbTheme.colors
                    .status.error.foreground)
                LaunchedEffect(requestedRow) {
                    if (requestedRow == row.rowId) {
                        focus.requestFocus()
                        requestedRow = null
                    }
                }
            }
        }
        SettingsButton("Добавить строку", {
            val id = (form.environment.maxOfOrNull { it.rowId } ?: -1L) + 1
            requestedRow = id
            onEdit(AcpConnectionFormChange.VariableAdded(id))
        }, Modifier.width(Layout.addVariableWidth), height = Layout.compactActionHeight, startAligned = true) {
            HbIcon(HbIconGlyph.Plus, tint = it, size = Aurora.Glyph)
        }
    }
}
