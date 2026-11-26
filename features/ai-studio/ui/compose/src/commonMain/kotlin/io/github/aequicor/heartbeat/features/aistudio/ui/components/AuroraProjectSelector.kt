package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState.Project
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState.ProjectChoice
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState.ProjectList
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_project_closed_icon
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.choose_another_project_folder
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.open_project_folder
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_folder_inaccessible
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_heading
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_label
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_list_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_list_loading
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_search_empty
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.retry_project_list
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.search_projects
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.select_project
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.ungrouped_sessions
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraProjectSelector(
    bootstrap: SessionBootstrapState,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = bootstrap.projectTitle ?: stringResource(Res.string.ungrouped_sessions)
    val triggerFocus = remember { FocusRequester() }
    var open by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    var readingFolder by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf(false) }
    val busy = bootstrap.operation != null || readingFolder
    val close = {
        open = false
        restoreFocus = true
    }
    val canRestoreFocus = !open && !busy && bootstrap.pendingProject == null
    RestoreProjectTriggerFocus(canRestoreFocus && restoreFocus, touch, triggerFocus) {
        restoreFocus = false
    }
    val openFolder = rememberProjectFolderPicker(
        enabled = !busy,
        onReading = { readingFolder = it },
        onError = { folderError = it },
        onSelectFolder = { path, name ->
            close()
            onAction(AiStudioAction.BootstrapProject.Add(path, name))
        },
    )
    SideEffect(bootstrap.error) {
        if (bootstrap.error == SessionBootstrapState.Error.InvalidWorkingDirectory) open = true
    }
    val triggerHeight = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget
    Box(modifier) {
        ProjectSelectorButton(title, touch, busy, triggerFocus) {
            open = true
            onAction(AiStudioAction.LoadBootstrapProjects)
        }
        if (open && !readingFolder && bootstrap.pendingProject == null) {
            ProjectChoicesOverlay(touch, triggerHeight, onDismiss = close) {
                ProjectChoices(
                    bootstrap, touch, busy,
                    folderError || bootstrap.error == SessionBootstrapState.Error.InvalidWorkingDirectory,
                    openFolder,
                    onRetry = { onAction(AiStudioAction.LoadBootstrapProjects) },
                    onSelect = { choice ->
                        val project = choice?.let { Project.Existing(it.id) } ?: Project.Ungrouped
                        onAction(AiStudioAction.BootstrapProject.Select(project, choice?.title))
                        close()
                    },
                )
            }
        }
    }
    AuroraProjectNameDialog(bootstrap, busy) { onAction(it) }
}

@Composable
private fun RestoreProjectTriggerFocus(
    restore: Boolean,
    touch: Boolean,
    triggerFocus: FocusRequester,
    onRestore: () -> Unit,
) {
    val finishRestoring by rememberUpdatedState(onRestore)
    LaunchedEffect(restore) {
        if (restore) {
            withFrameNanos { }
            if (!touch) triggerFocus.requestFocus()
            finishRestoring()
        }
    }
}

@Composable
private fun ProjectSelectorButton(
    title: String,
    touch: Boolean,
    busy: Boolean,
    triggerFocus: FocusRequester,
    onOpen: () -> Unit,
) {
    AuroraHeaderButton(
        stringResource(Res.string.select_project, title),
        onOpen,
        Modifier.focusRequester(triggerFocus).then(
            if (touch) Modifier.fillMaxWidth() else Modifier.auroraFloatingHeaderSurface(AuroraColors.FloatingHeaderSurface),
        ),
        height = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget,
        enabled = !busy,
        contentPadding = PaddingValues(horizontal = AuroraLayout.Space),
    ) {
        ProjectLabel(title, Modifier.weight(1f, fill = false))
        HbIcon(HbIconGlyph.ChevronDown, tint = AuroraColors.NavigationIcon, size = AuroraLayout.ContentInset)
    }
}

@Composable
private fun ProjectChoicesOverlay(
    touch: Boolean,
    triggerHeight: Dp,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (touch) Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.fillMaxSize().background(AuroraColors.NavigationInk.copy(alpha = 0.32f))
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            )
            content()
        }
    } else {
        val offset = with(LocalDensity.current) { (triggerHeight + AuroraLayout.ControlGap).roundToPx() }
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(0, offset),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
            content = content,
        )
    }
}

@Composable
internal fun AuroraStaticProject(title: String, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.project_label, title)
    ProjectLabel(title, modifier.clearAndSetSemantics { contentDescription = description })
}

@Composable
private fun ProjectLabel(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
    ) {
        Image(
            painterResource(Res.drawable.aurora_project_closed_icon),
            contentDescription = null,
            modifier = Modifier.size(AuroraLayout.Glyph),
            colorFilter = ColorFilter.tint(AuroraColors.NavigationIcon),
        )
        BasicText(
            title,
            Modifier.weight(1f, fill = false),
            style = HbTypography.Label.copy(color = AuroraColors.DesktopInk),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProjectChoices(
    bootstrap: SessionBootstrapState,
    touch: Boolean,
    busy: Boolean,
    folderError: Boolean,
    onOpenFolder: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (ProjectChoice?) -> Unit,
) {
    val choices = (bootstrap.projects as? ProjectList.Ready)?.choices.orEmpty()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (!touch) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
    val shape = if (touch) RoundedCornerShape(topStart = AuroraLayout.ContentInset, topEnd = AuroraLayout.ContentInset)
    else RoundedCornerShape(AuroraLayout.SmallRadius)
    val width = if (touch) Modifier.fillMaxWidth() else Modifier.widthIn(
        min = AuroraLayout.ProjectPickerWidth, max = AuroraLayout.ProjectPickerWidth,
    )
    Column(
        width
            .clip(shape)
            .auroraMenuBackground(shape, AuroraColors.NavigationSearchSurface)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            },
    ) {
        if (touch) TouchProjectListHeading()
        ProjectChoiceSearch(touch, query, { query = it }, busy, choices.isNotEmpty(), onOpenFolder)
        Box(Modifier.fillMaxWidth().height(AuroraLayout.FrameStroke).background(AuroraColors.NavigationSearchOutline))
        if (folderError) {
            AuroraText(stringResource(Res.string.project_folder_inaccessible), Modifier.padding(AuroraLayout.Space))
            AuroraChip(
                stringResource(Res.string.choose_another_project_folder), onOpenFolder,
                modifier = Modifier.padding(horizontal = AuroraLayout.Space), touch = touch, enabled = !busy,
            )
        }
        val selectedId = (bootstrap.project as? Project.Existing)?.id
        ProjectOption(
            stringResource(Res.string.ungrouped_sessions), selectedId == null, touch, !busy, { onSelect(null) },
            Modifier.focusRequester(focus),
        )
        ProjectChoiceItems(bootstrap.projects, query, selectedId, touch, busy, onRetry, onSelect)
    }
}

@Composable
private fun ColumnScope.TouchProjectListHeading() {
    Box(
        Modifier.align(Alignment.CenterHorizontally).padding(top = AuroraLayout.SmallGap)
            .size(AuroraLayout.TouchTarget, AuroraLayout.SmallGap)
            .clip(RoundedCornerShape(AuroraLayout.SmallGap)).background(AuroraColors.NavigationSearchOutline),
    )
    AuroraHeaderText(
        stringResource(Res.string.project_heading),
        style = HbTypography.PaneTitle,
        modifier = Modifier.padding(AuroraLayout.Space),
    )
}

@Composable
private fun ProjectChoiceSearch(
    touch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    busy: Boolean,
    searchEnabled: Boolean,
    onOpenFolder: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    AuroraActionSearchRow(
        actionLabel = stringResource(Res.string.open_project_folder),
        searchLabel = stringResource(Res.string.search_projects),
        expanded = searching,
        appliedQuery = query,
        onAction = onOpenFolder,
        onExpand = { searching = true },
        onCollapse = {
            searching = false
            onQueryChange("")
        },
        onSearch = onQueryChange,
        modifier = Modifier.padding(AuroraLayout.Space),
        actionEnabled = !busy,
        searchEnabled = searchEnabled,
        target = if (touch) AuroraLayout.TouchTarget else AuroraLayout.NavigationRow,
        actionBackground = AuroraColors.NavigationSearchSurface,
        actionInk = AuroraColors.DesktopInk,
        actionHoverBackground = AuroraColors.NavigationHover,
    ) {
        HbIcon(HbIconGlyph.FolderOpen, tint = AuroraColors.NavigationIcon, size = AuroraLayout.Glyph)
    }
}

@Composable
private fun ProjectChoiceItems(
    projects: ProjectList?,
    query: String,
    selectedId: String?,
    touch: Boolean,
    busy: Boolean,
    onRetry: () -> Unit,
    onSelect: (ProjectChoice?) -> Unit,
) {
    when (projects) {
        ProjectList.Loading, null ->
            AuroraText(stringResource(Res.string.project_list_loading), Modifier.padding(AuroraLayout.Space))
        ProjectList.Failed -> Column {
            AuroraText(stringResource(Res.string.project_list_failed), Modifier.padding(AuroraLayout.Space))
            AuroraChip(
                stringResource(Res.string.retry_project_list), onRetry,
                modifier = Modifier.padding(horizontal = AuroraLayout.Space), touch = touch,
            )
        }
        is ProjectList.Ready -> {
            val results = remember(projects.choices, query) {
                projects.choices.filter { it.title.contains(query.trim(), ignoreCase = true) }
            }
            if (results.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = AuroraLayout.ProjectListMaxHeight)) {
                    items(results, key = ProjectChoice::id) { choice ->
                        ProjectOption(choice.title, choice.id == selectedId, touch, !busy, { onSelect(choice) })
                    }
                }
            } else if (query.isNotBlank()) {
                AuroraText(stringResource(Res.string.project_search_empty), Modifier.padding(AuroraLayout.Space))
            }
        }
    }
}

@Composable
private fun ProjectOption(
    title: String,
    active: Boolean,
    touch: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = if (touch) AuroraLayout.TouchTarget else AuroraLayout.ProjectOptionHeight)
            .background(if (active) AuroraColors.NavigationSelected else Color.Transparent)
            .semantics { selected = active }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = AuroraLayout.ContentInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectLabel(title, Modifier.weight(1f))
        if (active) HbIcon(HbIconGlyph.Check, tint = AuroraColors.Primary, size = AuroraLayout.Glyph)
    }
}
