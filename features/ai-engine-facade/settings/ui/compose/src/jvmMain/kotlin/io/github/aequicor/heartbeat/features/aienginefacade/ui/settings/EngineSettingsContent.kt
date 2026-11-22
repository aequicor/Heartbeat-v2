package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.ic_settings_back
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction as Action
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State

@Composable
internal fun EngineSettingsContent(
    state: State,
    localHost: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var firstLoad by remember { mutableStateOf(true) }
    BoxWithConstraints(modifier.fillMaxSize().background(HbTheme.colors.surface.base)) {
        val compact = maxWidth < Layout.twoPaneBreakpoint
        val loaded = state as? State.Loaded
        SideEffect(loaded != null) {
            if (firstLoad && loaded != null) {
                firstLoad = false
                if (!compact) loaded.initialEngineId()?.let { onAction(Action.Select(it)) }
            }
        }
        Column {
            if (compact && loaded != null) {
                CompactSettingsToolbar(loaded, onAction)
            }
            SettingsPanes(state, compact, localHost, onAction)
        }
        loaded?.dialog?.let { DecisionDialog(it, loaded, onAction) }
    }
}

private fun State.Loaded.initialEngineId(): String? {
    if (editor != null || notice != null || requestedField != null) return null
    return rows.firstOrNull()?.id
}

@Composable
private fun SettingsPanes(
    state: State,
    compact: Boolean,
    localHost: Boolean,
    onAction: (Action) -> Unit,
) {
    val loaded = state as? State.Loaded
    val showEditor = loaded?.editor != null && loaded.requestedField == null &&
        (!compact || loaded.renameId == null)
    Row(Modifier.fillMaxSize()) {
        if (!compact || !showEditor) {
            val width = if (compact) Modifier.weight(1f) else Modifier.width(Layout.listWidth - Aurora.Stroke)
            EngineList(state, onAction, width)
        }
        if (!compact) PaneDivider()
        if (loaded != null && showEditor) {
            EditorPane(loaded, compact, localHost, onAction, Modifier.weight(1f))
        }
    }
}

// The divider is a sibling of the toolbar in the parent column.
@Suppress("MultipleEmitters")
@Composable
private fun CompactSettingsToolbar(loaded: State.Loaded, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    val back = when {
        loaded.requestedField != null -> Action.CancelFieldSearch
        loaded.renameId != null -> Action.CancelRename
        loaded.editor != null -> Action.BackToList
        else -> return
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsButton("К списку", { onAction(back) }, variant = HbButtonVariant.Ghost) {
            SettingsIcon(Res.drawable.ic_settings_back, it)
        }
    }
    SettingsRule()
}

@Composable
private fun EditorPane(
    state: State.Loaded,
    compact: Boolean,
    localHost: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = state.editor ?: return
    val inset = if (compact) HbSpacing.M else Layout.editorInset
    key(editor.engineId) {
        Column(modifier.fillMaxHeight().padding(horizontal = inset)
            .padding(top = HbSpacing.L, bottom = if (compact) HbSpacing.M else HbSpacing.Xl)) {
            when (editor) {
                is State.Editor.Engine -> LocalEngineEditor(editor, state, onAction)
                is State.Editor.Connection -> {
                    val title = when {
                        editor.engineId != null -> "Подключение ACP"
                        editor.changed -> "Новое подключение"
                        else -> "Добавить подключение"
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = Layout.fieldHeight),
                        verticalAlignment = Alignment.CenterVertically) {
                        SettingsText(title, Modifier.weight(1f), HbTypography.PageTitle)
                        if (editor.engineId != null) SettingsButton(
                            "Настройки движка", { onAction(Action.OpenEngineSettings) },
                            if (compact) Modifier else Modifier.width(Layout.engineSettingsWidth), startAligned = true,
                        )
                    }
                    Spacer(Modifier.height(Layout.headingGap))
                    AcpConnectionEditor(editor, state, localHost, onAction)
                }
            }
        }
    }
}

@Composable
private fun PaneDivider() {
    Spacer(Modifier.fillMaxHeight().width(Aurora.Stroke).background(HbTheme.colors.border.subtle))
}

@Composable
private fun DecisionDialog(dialog: State.Dialog, state: State.Loaded, onAction: (Action) -> Unit) {
    val close = { if (!state.saving) onAction(Action.DismissDialog) }
    SettingsModal(Layout.decisionWidth, close) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = HbSpacing.L,
            vertical = Aurora.DocumentHeadingGap)) {
            when (dialog) {
                is State.Dialog.Delete -> {
                    SettingsText("Удалить движок?", style = HbTypography.Title)
                    Spacer(Modifier.height(Aurora.DocumentHeadingGap))
                    SettingsText("«${dialog.engineName}» исчезнет из списка.", style = HbTypography.PaneTitle)
                    Spacer(Modifier.height(Layout.fieldGroupGap))
                    SettingsText("ACP-подключение «${dialog.connectionName}» сохранится.",
                        color = HbTheme.colors.text.secondary)
                    Spacer(Modifier.height(Layout.fieldHeight))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HbSpacing.S,
                        Alignment.End)) {
                        SettingsButton("Отмена", close, Modifier.width(Layout.cancelWidth),
                            enabled = !state.saving, height = Layout.actionHeight)
                        SettingsButton("Удалить", { onAction(Action.ConfirmDelete(dialog.id)) },
                            Modifier.width(Layout.deleteWidth), variant = HbButtonVariant.Destructive,
                                enabled = !state.saving, height = Layout.actionHeight)
                    }
                }
                State.Dialog.Unsaved -> {
                    SettingsText("Сохранить изменения?", style = HbTypography.Title)
                    Spacer(Modifier.height(HbSpacing.M))
                    SettingsButton("Сохранить", { onAction(Action.Save) }, variant = HbButtonVariant.Primary,
                        enabled = !state.saving && (state.editor as? State.Editor.Connection)?.form?.canSave != false)
                    Spacer(Modifier.height(HbSpacing.S))
                    SettingsButton("Продолжить редактирование", close, enabled = !state.saving)
                    Spacer(Modifier.height(HbSpacing.S))
                    SettingsButton("Выйти без сохранения", { onAction(Action.Discard) },
                        variant = HbButtonVariant.Ghost,
                        enabled = !state.saving)
                }
            }
            state.notice?.let {
                SettingsText(it, Modifier.padding(top = HbSpacing.M), color = HbTheme.colors.status.error.foreground)
            }
        }
    }
}
