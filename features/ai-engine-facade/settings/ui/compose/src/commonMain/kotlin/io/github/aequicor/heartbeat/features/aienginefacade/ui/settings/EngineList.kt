package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.designsystem.components.HbMenuSeparator
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction as Action
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_acp_agent
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_back
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_chat
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.ic_settings_trash
import io.github.aequicor.heartbeat.features.aienginefacade.ui.resources.Res

@Composable
internal fun EngineList(state: State, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val loaded = state as? State.Loaded
    val requestedField = loaded?.requestedField
    val rows = loaded?.rows.orEmpty().filter {
        requestedField != null || it.name.contains(query.trim(), ignoreCase = true) || it.id == loaded?.renameId
    }
    val list = rememberLazyListState()
    LaunchedEffect(loaded?.renameId) {
        val index = rows.indexOfFirst { it.id == loaded?.renameId }
        if (index >= 0) list.scrollToItem(index)
    }
    Column(modifier.fillMaxHeight().background(HbTheme.colors.surface.raised).padding(top = HbSpacing.M)) {
        EngineListHeading(loaded?.rows.isNullOrEmpty())
        Spacer(Modifier.height(Aurora.DocumentHeadingGap))
        EngineListToolbar(
            query = query,
            loaded = loaded != null,
            selectingField = requestedField != null,
            onQueryChange = { query = it },
            onAction = onAction,
        )
        Spacer(Modifier.height(Layout.headingGap))
        val inset = Modifier.padding(horizontal = Aurora.DocumentHeadingGap)
        when (state) {
            State.Loading -> SettingsText("Загрузка подключений…", inset, color = HbTheme.colors.text.secondary)
            is State.Failed -> Column(inset, verticalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
                SettingsText(state.failure.message, color = HbTheme.colors.status.error.foreground)
                EngineErrorActions(state.failure) { onAction(Action.Load) }
            }
            is State.Loaded -> LoadedEngineList(state, rows, requestedField != null, onAction, list)
        }
    }
}

// The empty-state message and list share the caller column without an extra layout.
@Suppress("MultipleEmitters")
@Composable
private fun LoadedEngineList(
    state: State.Loaded,
    rows: List<State.Row>,
    selectingField: Boolean,
    onAction: (Action) -> Unit,
    list: LazyListState,
) {
    val inset = Modifier.padding(horizontal = Aurora.DocumentHeadingGap)
    val notice = state.notice
    if ((state.editor == null || state.renameId != null) && notice != null) {
        SettingsText(notice, inset.padding(bottom = HbSpacing.S))
    }
    if (rows.isEmpty()) {
        SettingsText(
            if (state.rows.isEmpty()) "Пока нет подключений" else "Движки не найдены",
            inset,
            color = HbTheme.colors.text.secondary,
        )
    }
    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = Aurora.ContentInset),
        state = list,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        items(rows, key = { it.id }) { row ->
            EngineRow(row, row.id == state.editor?.engineId, row.id == state.renameId, selectingField, onAction)
        }
    }
}

// Header pieces share the parent column so the tab underline retains its geometry.
@Suppress("MultipleEmitters")
@Composable
private fun EngineListHeading(empty: Boolean) {
    SettingsText(
        if (empty) "Подключения" else "Движки",
        Modifier.padding(horizontal = Aurora.DocumentHeadingGap),
        HbTypography.PageTitle,
    )
    Spacer(Modifier.height(Aurora.ContentInset))
    Row(
        Modifier.padding(horizontal = Aurora.DocumentHeadingGap),
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(Res.drawable.ic_settings_chat, HbTheme.colors.accent.foreground, Aurora.LargeGlyph)
        SettingsText("Чат", color = HbTheme.colors.accent.foreground)
    }
    Spacer(Modifier.height(Aurora.ChromeInset))
    Box(Modifier.fillMaxWidth().padding(horizontal = Aurora.DocumentHeadingGap)) {
        SettingsRule(Modifier.align(Alignment.BottomStart))
        Spacer(Modifier.width(Layout.tabWidth).height(Aurora.FrameStroke).background(HbTheme.colors.accent.brand))
    }
}

@Composable
private fun EngineListToolbar(
    query: String,
    loaded: Boolean,
    selectingField: Boolean,
    onQueryChange: (String) -> Unit,
    onAction: (Action) -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HbSpacing.M),
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            selectingField -> {
                SettingsText("Выберите движок", Modifier.weight(1f))
                SettingsButton("Отмена", { onAction(Action.CancelFieldSearch) }, variant = HbButtonVariant.Ghost)
            }
            searching && loaded -> {
                val closeSearch = {
                    searching = false
                    onQueryChange("")
                }
                SettingsButton(
                    "", closeSearch, Modifier.width(Layout.fieldHeight), description = "Закрыть поиск движков",
                ) {
                    SettingsIcon(Res.drawable.ic_settings_back, it)
                }
                val focus = remember { FocusRequester() }
                SettingsInput(
                    query, onQueryChange, "Найти движок",
                    Modifier.weight(1f).focusRequester(focus).dismissOnEscape(closeSearch),
                    placeholder = "Найти движок", trailing = { HbIcon(HbIconGlyph.Search, size = Aurora.Glyph) },
                )
                SideEffect(focus) { focus.requestFocus() }
            }
            else -> {
                SettingsButton(
                    "Добавить подключение", { onAction(Action.AddConnection) }, Modifier.weight(1f),
                    variant = HbButtonVariant.Primary, enabled = loaded,
                ) {
                    HbIcon(HbIconGlyph.Plus, tint = it, size = Aurora.Glyph)
                }
                SettingsButton(
                    "", { searching = true }, Modifier.width(Layout.fieldHeight),
                    enabled = loaded, description = "Поиск движков",
                ) {
                    HbIcon(HbIconGlyph.Search, tint = it, size = Aurora.LargeGlyph)
                }
            }
        }
    }
}

@Composable
private fun EngineRow(
    row: State.Row,
    active: Boolean,
    renaming: Boolean,
    selectingField: Boolean,
    onAction: (Action) -> Unit,
) {
    val shape = RoundedCornerShape(Aurora.ChromeInset)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val colors = HbTheme.colors
    val fill = when {
        active || renaming -> colors.selection.background
        hovered -> colors.surface.subtle
        else -> Color.Transparent
    }
    Row(Modifier.fillMaxWidth().heightIn(min = Layout.rowHeight).clip(shape).background(fill)
        .then(if (focused) Modifier.border(Aurora.Stroke, colors.focus, shape) else Modifier)
        .padding(start = Aurora.ContentInset, end = HbSpacing.S), verticalAlignment = Alignment.CenterVertically) {
        Row((if (renaming) Modifier else Modifier.weight(1f)).heightIn(min = Layout.rowHeight)
            .clickable(interactionSource = interaction, indication = null, enabled = !renaming) {
                onAction(Action.Select(row.id))
            }
            .semantics { selected = active }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Aurora.ContentInset)) {
            Box(Modifier.size(Layout.fieldHeight).background(colors.surface.base, HbShapes.Medium)
                .border(Aurora.Stroke, colors.border.subtle, HbShapes.Medium), contentAlignment = Alignment.Center) {
                SettingsIcon(Res.drawable.ic_acp_agent, colors.text.secondary, Aurora.RequestGlyph)
            }
            if (!renaming) Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
                SettingsText(row.name, style = HbTypography.PaneTitle, maxLines = 1)
                SettingsText(row.subtitle, style = HbTypography.Metadata, color = colors.text.secondary, maxLines = 1)
            }
        }
        if (renaming) {
            RenameEngine(row, onAction, Modifier.weight(1f).padding(start = HbSpacing.S))
        } else if (!selectingField) {
            EngineMenu(row, onAction)
        }
    }
}

@Composable
private fun EngineMenu(row: State.Row, onAction: (Action) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    HbMenu(expanded = menu, onDismissRequest = { menu = false }, alignEnd = true,
        presentation = HbMenuPresentation.AuroraSettings,
        actions = listOf(
            HbMenuAction("Переименовать", { onAction(Action.StartRename(row.id)) }, glyph = HbIconGlyph.Pencil),
            HbMenuAction("Создать копию", { onAction(Action.Copy(row.id)) }, glyph = HbIconGlyph.Copy),
            HbMenuSeparator,
            HbMenuAction(
                "Удалить", { onAction(Action.Delete(row.id)) }, destructive = true,
                leadingIcon = { color -> SettingsIcon(Res.drawable.ic_settings_trash, color, Aurora.LargeGlyph) },
            ),
        )) { trigger ->
        SettingsButton(
            "", { menu = true }, trigger.width(Layout.fieldHeight),
            variant = HbButtonVariant.Ghost, description = "Действия движка ${row.name}",
        ) {
            HbIcon(
                HbIconGlyph.More, Modifier.rotate(RowMenuRotation), tint = HbTheme.colors.text.secondary,
                    size = Aurora.Glyph,
            )
        }
    }
}

@Composable
private fun RenameEngine(row: State.Row, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    var value by remember(row.id) { mutableStateOf(TextFieldValue(row.name, TextRange(0, row.name.length))) }
    val submit = { if (value.text.isNotBlank()) onAction(Action.ConfirmRename(row.id, value.text)) }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(value, { value = it },
            modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Имя движка" }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter -> { submit(); true }
                        Key.Escape -> { onAction(Action.CancelRename); true }
                        else -> false
                    }
                }.background(HbTheme.colors.surface.input, HbShapes.Medium)
                .border(Aurora.Stroke, HbTheme.colors.focus, HbShapes.Medium).padding(HbSpacing.S),
            singleLine = true, textStyle = HbTypography.Label.copy(color = HbTheme.colors.text.primary))
        SettingsButton(
            "", submit, Modifier.width(Layout.compactActionHeight), enabled = value.text.isNotBlank(),
            variant = HbButtonVariant.Ghost, description = "Сохранить имя",
        ) {
            HbIcon(HbIconGlyph.Check, tint = it, size = Aurora.Glyph)
        }
    }
    SideEffect(focus) { focus.requestFocus() }
}

private const val RowMenuRotation = 90f
