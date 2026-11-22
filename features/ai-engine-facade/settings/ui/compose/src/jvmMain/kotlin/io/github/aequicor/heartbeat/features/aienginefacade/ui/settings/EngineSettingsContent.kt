package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraSettingsTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction as Action
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_back
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_cpu
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_pulse
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.Res

@Composable
internal fun EngineSettingsContent(
    state: State,
    localHost: Boolean,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var firstLoad by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf(false) }
    HbAuroraSettingsTheme {
        Column(modifier.fillMaxSize().background(HbTheme.colors.surface.base)) {
            SettingsCaption()
            BoxWithConstraints(Modifier.weight(1f)) {
                val compact = maxWidth < Layout.threePaneBreakpoint
                val loaded = state as? State.Loaded
                SideEffect(loaded != null) {
                    if (firstLoad && loaded != null) {
                        firstLoad = false
                        if (!compact && loaded.editor == null && loaded.notice == null) {
                            loaded.rows.firstOrNull()?.let { onAction(Action.Select(it.id)) }
                        }
                    }
                }
                Column {
                    if (compact) {
                        CompactSettingsToolbar(loaded, onAction, onSearch = { search = true })
                    }
                    SettingsPanes(state, compact, localHost, onAction, onSearch = { search = true })
                }
            }
        }
        if (search) EngineSettingSearch(onSelect = {
            search = false
            onAction(Action.FindSetting(it))
        }, onDismiss = { search = false })
        if (state is State.Loaded) state.dialog?.let { DecisionDialog(it, state, onAction) }
    }
}

@Composable
private fun SettingsPanes(
    state: State,
    compact: Boolean,
    localHost: Boolean,
    onAction: (Action) -> Unit,
    onSearch: () -> Unit,
) {
    val loaded = state as? State.Loaded
    val showEditor = loaded?.editor != null && loaded.requestedField == null &&
        (!compact || loaded.renameId == null)
    Row(Modifier.fillMaxSize()) {
        if (!compact) {
            SettingsSidebar({ onAction(Action.Close) }, onSearch, { onAction(Action.BackToList) })
            PaneDivider()
        }
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
private fun CompactSettingsToolbar(loaded: State.Loaded?, onAction: (Action) -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val back = when {
            loaded?.requestedField != null -> Action.CancelFieldSearch
            loaded?.renameId != null -> Action.CancelRename
            else -> Action.Back
        }
        SettingsButton("Назад", { onAction(back) }, variant = HbButtonVariant.Ghost) {
            SettingsIcon(Res.drawable.ic_settings_back, it)
        }
        SettingsText("Настройки", Modifier.weight(1f), HbTypography.PaneTitle)
        SettingsButton("", onSearch, Modifier.width(Layout.fieldHeight), description = "Найти настройку") {
            HbIcon(HbIconGlyph.Search, tint = it)
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
private fun SettingsCaption() {
    val caption = HbWindowCaption.current
    if (caption.height <= Dp.Hairline) return
    val mac = caption.leftInset > Dp.Hairline
    Box(Modifier.fillMaxWidth().height(maxOf(caption.height, Layout.fieldHeight)).background(HbTheme.colors
        .surface.raised)) {
        if (mac) {
            SettingsText("Heartbeat", Modifier.align(Alignment.Center))
        } else {
            Row(Modifier.align(Alignment.CenterStart).padding(start = HbSpacing.M, end = caption.rightInset),
                horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
                    verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(Res.drawable.ic_settings_pulse, HbTheme.colors.accent.foreground, Aurora.RequestGlyph)
                SettingsText("Heartbeat")
            }
        }
        SettingsRule(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PaneDivider() {
    Spacer(Modifier.fillMaxHeight().width(Aurora.Stroke).background(HbTheme.colors.border.subtle))
}

@Composable
private fun SettingsSidebar(onBack: () -> Unit, onSearch: () -> Unit, onEngines: () -> Unit) {
    Column(Modifier.width(Layout.navigationWidth - Aurora.Stroke).fillMaxHeight().background(HbTheme.colors
        .surface.subtle)
        .padding(horizontal = Aurora.ContentInset).padding(top = Aurora.ChromeInset)) {
        SettingsButton("Назад в чат", onBack, variant = HbButtonVariant.Ghost,
            height = Layout.compactActionHeight, horizontalInset = HbSpacing.S) {
            SettingsIcon(Res.drawable.ic_settings_back, it)
        }
        Spacer(Modifier.height(Layout.fieldGroupGap))
        SettingsText("Настройки", Modifier.padding(horizontal = Aurora.ContentInset), HbTypography.PageTitle)
        Spacer(Modifier.height(Aurora.DocumentHeadingGap))
        SettingsButton("Найти настройку", onSearch, Modifier.fillMaxWidth(), startAligned = true,
            tint = HbTheme.colors.text.secondary) {
            HbIcon(HbIconGlyph.Search, tint = it, size = Aurora.LargeGlyph)
        }
        Spacer(Modifier.height(Aurora.ContentInset))
        SettingsButton("Движки", onEngines, Modifier.fillMaxWidth().semantics { selected = true },
            height = Layout.actionHeight, startAligned = true, tint = HbTheme.colors.accent.foreground) {
            SettingsIcon(Res.drawable.ic_settings_cpu, it, Aurora.LargeGlyph)
        }
    }
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
