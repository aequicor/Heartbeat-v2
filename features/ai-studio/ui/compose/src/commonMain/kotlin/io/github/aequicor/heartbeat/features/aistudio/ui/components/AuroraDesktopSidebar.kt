package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowClientArea
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.component.ProjectsSessionsComponent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_new_chat_icon
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_project_closed_icon
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_project_new_chat_icon
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_project_open_icon
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.collapse_child_sessions
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.expand_child_sessions
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_project_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.scroll_sessions
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.search_sessions
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_needs_answer
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_new_result
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_working
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.settings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraDesktopSidebar(
    state: AiStudioScreenState,
    onAction: (AiStudioAction) -> Unit,
    onOpenSettings: () -> Unit,
    projectsSessions: ProjectsSessionsComponent,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val selectedSessionFocus = rememberSelectedSessionFocus(list, state.navigation)
    Column(modifier.fillMaxHeight()) {
        SidebarHeader(state.chatSearchVisible, onAction, onOpenSettings)
        if (state.chatSearchVisible) {
            Box(Modifier.width(AuroraLayout.NavigationWidth).padding(top = AuroraLayout.SmallGap)) {
                AuroraSearch(
                    stringResource(Res.string.search_sessions),
                    state.chatSearch,
                    { onAction(AiStudioAction.SearchChats(it)) },
                    false,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = AuroraLayout.SmallGap, bottom = AuroraLayout.Space)) {
            LazyColumn(
                Modifier.fillMaxHeight().fillMaxWidth(),
                state = list,
            ) {
                itemsIndexed(state.navigation, key = { _, item -> item.id }, contentType = { _, item -> item.kind }) {
                    index, row ->
                    val gap = when {
                        index == 0 -> Dp.Hairline
                        row.isProjectGroup -> AuroraLayout.NavigationProjectGap
                        else -> AuroraLayout.NavigationRowGap
                    }
                    val rowModifier = Modifier.width(AuroraLayout.NavigationWidth).padding(top = gap)
                    if (row.kind.isPagination) {
                        AuroraMoreChats(
                            row.displayTitle(),
                            { row.loadNextPage(projectsSessions) },
                            rowModifier,
                        )
                    } else {
                        val focus = if (row.selected) Modifier.focusRequester(selectedSessionFocus) else Modifier
                        SidebarRow(row, onAction, projectsSessions, rowModifier, titleFocusModifier = focus)
                    }
                }
            }
            AuroraHistoryScrollbar(
                list,
                stringResource(Res.string.scroll_sessions),
                Modifier.align(Alignment.CenterEnd).padding(end = AuroraLayout.SmallGap / 2),
                reverse = false,
            )
        }
    }
}

@Composable
private fun SidebarHeader(
    searchVisible: Boolean,
    onAction: (AiStudioAction) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.width(AuroraLayout.NavigationWidth)) {
        Row(
            Modifier.heightIn(min = AuroraLayout.NavigationHeading),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiStudioBrandMark(Modifier.size(AuroraLayout.Glyph), AuroraColors.Primary)
            SidebarText(
                "Heartbeat", Modifier.padding(start = AuroraLayout.NavigationCaptionGap), style = HbTypography.Label,
            )
            Spacer(Modifier.width(AuroraLayout.Space))
            HbWindowClientArea { clientArea ->
                AuroraControl(
                    onOpenSettings,
                    clientArea,
                    label = stringResource(Res.string.settings),
                    background = Color.Transparent,
                    horizontalPadding = (AuroraLayout.DesktopTarget - AuroraLayout.NavigationSymbol) / 2,
                ) {
                    HbIcon(HbIconGlyph.Settings, tint = AuroraColors.NavigationIcon,
                        size = AuroraLayout.NavigationSymbol,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = AuroraLayout.Space),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
        ) {
            AuroraControl(
                { onAction(AiStudioAction.NewChat) },
                Modifier.weight(1f),
                background = AuroraColors.NavigationPrimaryAction,
                focus = AuroraColors.NavigationOnPrimaryAction,
                round = false, horizontalPadding = AuroraLayout.ChromeInset,
                minimumTarget = AuroraLayout.NavigationRow,
            ) {
                Image(painterResource(Res.drawable.aurora_new_chat_icon), null, Modifier.size(AuroraLayout.Glyph))
                BasicText(
                    stringResource(Res.string.new_session),
                    Modifier.padding(start = AuroraLayout.ControlGap)
                        .padding(vertical = AuroraLayout.NavigationVerticalInset),
                    style = HbTypography.Label.copy(color = AuroraColors.NavigationOnPrimaryAction),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            SidebarSearchButton(searchVisible, onAction)
        }
    }
}

@Composable
private fun SidebarRow(
    row: NavigationRow,
    onAction: (AiStudioAction) -> Unit,
    projectsSessions: ProjectsSessionsComponent,
    modifier: Modifier = Modifier,
    titleFocusModifier: Modifier = Modifier,
) {
    val project = row.isProjectGroup
    val session = row.kind == NavigationKind.Chat
    val child = row.depth > 0
    val expanded = row.expanded
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val focusGroupModifier = if (session) Modifier.onFocusChanged { focused = it.hasFocus }.focusGroup() else Modifier
    Row(
        modifier.fillMaxWidth().heightIn(min = AuroraLayout.NavigationRow)
            .hoverable(interaction)
            .then(focusGroupModifier)
            .navigationBackground(row.selected, hovered)
            .then(if (child) Modifier.branch(row.lastChild) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (session && expanded != null) {
                val label = if (expanded) Res.string.collapse_child_sessions else Res.string.expand_child_sessions
                SidebarAction(
                    stringResource(label),
                    { projectsSessions.setSessionExpanded(row.id, !expanded) },
                    Modifier.width(AuroraLayout.DesktopTarget),
                ) { SidebarChevron(expanded) }
            }
            val indentation = when {
                child -> AuroraLayout.NavigationChildIndent
                expanded != null && session -> AuroraLayout.FrameStroke
                project -> Dp.Hairline
                else -> AuroraLayout.NavigationIndent
            }
            val open: () -> Unit = if (session) {
                { onAction(AiStudioAction.OpenChat(row.id)) }
            } else {
                { projectsSessions.setProjectExpanded(row.project, expanded != true) }
            }
            SidebarTitle(row, open, indentation, Modifier.weight(1f).then(titleFocusModifier))
        }
        if (project) {
            SidebarAction(
                stringResource(Res.string.new_project_session, row.displayTitle()),
                { onAction(AiStudioAction.NewProjectChat(row.project)) },
                Modifier.width(AuroraLayout.DesktopTarget),
            ) {
                Image(
                    painterResource(Res.drawable.aurora_project_new_chat_icon), null,
                    Modifier.size(AuroraLayout.Glyph),
                    colorFilter = ColorFilter.tint(AuroraColors.NavigationIcon),
                )
            }
        } else {
            AuroraSessionActions(row, archiveVisible = hovered || focused, onAction = onAction)
        }
    }
}

@Composable
private fun SidebarTitle(
    row: NavigationRow,
    onClick: () -> Unit,
    indentation: Dp,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hasFocus by interaction.collectIsFocusedAsState()
    val showFocus = hasFocus && HbFocusIndication.keyboardNavigation
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    Row(
        modifier.heightIn(min = AuroraLayout.NavigationRow)
            .clip(shape)
            .then(if (showFocus) Modifier.border(AuroraLayout.Stroke, AuroraColors.Primary, shape) else Modifier)
            .semantics { if (row.kind == NavigationKind.Chat) selected = row.selected }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(
                start = indentation,
                top = AuroraLayout.NavigationVerticalInset, bottom = AuroraLayout.NavigationVerticalInset,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.isProjectGroup) {
            SidebarProjectTitle(row)
        } else if (row.kind == NavigationKind.Chat) {
            val style = HbTypography.Label.copy(color = AuroraColors.DesktopInk)
            AuroraFadingText(
                row.displayTitle(),
                style,
                AuroraLayout.NavigationTitleFade,
                Modifier.weight(1f, fill = false),
            )
        } else {
            SidebarText(row.displayTitle(), Modifier.weight(1f, fill = false), color = AuroraColors.NavigationMuted)
        }
    }
}

@Composable
private fun RowScope.SidebarProjectTitle(row: NavigationRow) {
    if (row.kind == NavigationKind.Section) {
        HbIcon(HbIconGlyph.Message, tint = AuroraColors.NavigationIcon, size = AuroraLayout.NavigationSymbol)
    } else {
        val folder = if (row.expanded == false) {
            Res.drawable.aurora_project_closed_icon
        } else {
            Res.drawable.aurora_project_open_icon
        }
        Image(
            painterResource(folder), null, Modifier.size(AuroraLayout.NavigationSymbol),
            colorFilter = ColorFilter.tint(AuroraColors.NavigationIcon),
        )
    }
    Spacer(Modifier.width(AuroraLayout.Space))
    BasicText(
        row.displayTitle(),
        Modifier.weight(1f, fill = false),
        style = HbTypography.Label.copy(color = AuroraColors.DesktopInk),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

private val NavigationRow.isProjectGroup: Boolean
    get() = kind == NavigationKind.Project || kind == NavigationKind.Section

@Composable
internal fun SidebarText(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = AuroraColors.Ink,
    style: TextStyle = HbTypography.Label,
) {
    BasicText(
        label, modifier,
        style.copy(color = color),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun SidebarAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val showFocus = focused && HbFocusIndication.keyboardNavigation
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    Box(
        modifier.heightIn(min = AuroraLayout.NavigationRow).clip(shape)
            .then(if (hovered) Modifier.background(AuroraColors.NavigationHover) else Modifier)
            .then(if (showFocus) Modifier.border(AuroraLayout.Stroke, AuroraColors.Primary, shape) else Modifier)
            .hoverable(interaction)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun SidebarChevron(expanded: Boolean) {
    Canvas(Modifier.size(AuroraLayout.Glyph)) {
        val quarter = size.width / 4
        val middle = size.width / 2
        val path = Path().apply {
            if (expanded) {
                moveTo(quarter, middle - quarter / 2)
                lineTo(middle, middle + quarter / 2)
                lineTo(size.width - quarter, middle - quarter / 2)
            } else {
                moveTo(middle - quarter / 2, quarter)
                lineTo(middle + quarter / 2, middle)
                lineTo(middle - quarter / 2, size.height - quarter)
            }
        }
        drawPath(
            path, AuroraColors.DesktopInk,
            style = Stroke(AuroraLayout.NavigationChevronStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
internal fun SidebarStatus(status: NavigationStatus, modifier: Modifier = Modifier) {
    val labelResource = when (status) {
        NavigationStatus.Unread -> Res.string.session_new_result
        NavigationStatus.Working -> Res.string.session_working
        NavigationStatus.Attention -> Res.string.session_needs_answer
    }
    val label = stringResource(labelResource)
    Box(modifier.height(AuroraLayout.NavigationRow), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(AuroraLayout.NavigationStatus)
                    .semantics { contentDescription = label },
            ) {
                when (status) {
                    NavigationStatus.Unread -> drawCircle(AuroraColors.SessionUnread)
                    NavigationStatus.Working -> drawCircle(
                        AuroraColors.SessionWorking, style = Stroke(AuroraLayout.Stroke.toPx()),
                    )
                    NavigationStatus.Attention -> {
                        val diamond = Path().apply {
                            moveTo(center.x, 0f)
                            lineTo(size.width, center.y)
                            lineTo(center.x, size.height)
                            lineTo(0f, center.y)
                            close()
                        }
                        drawPath(diamond, AuroraColors.SessionAttention)
                    }
                }
            }
    }
}

private fun Modifier.navigationBackground(selected: Boolean, hovered: Boolean): Modifier = when {
    selected -> drawBehind {
        val inset = AuroraLayout.FrameStroke.toPx()
        drawRoundRect(
            AuroraColors.NavigationSelected,
            topLeft = Offset(0f, inset), size = Size(size.width, size.height - inset * 2),
            cornerRadius = CornerRadius(AuroraLayout.SmallRadius.toPx()),
        )
    }
    hovered -> background(AuroraColors.NavigationHover, RoundedCornerShape(AuroraLayout.SmallRadius))
    else -> this
}

private fun Modifier.branch(last: Boolean): Modifier = drawWithCache {
    val x = AuroraLayout.Glyph.toPx()
    val y = size.height / 2
    val stroke = AuroraLayout.Stroke.toPx()
    val color = AuroraColors.NavigationBranch
    val bend = if (last) Path().apply {
        val width = AuroraLayout.SmallRadius.toPx()
        val height = AuroraLayout.NavigationBranchBendHeight.toPx().coerceAtMost(y)
        moveTo(x, 0f)
        lineTo(x, y - height)
        quadraticTo(x, y, x + width, y)
        lineTo(x + AuroraLayout.Glyph.toPx(), y)
    } else null
    onDrawBehind {
        if (bend == null) drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
        else drawPath(bend, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
