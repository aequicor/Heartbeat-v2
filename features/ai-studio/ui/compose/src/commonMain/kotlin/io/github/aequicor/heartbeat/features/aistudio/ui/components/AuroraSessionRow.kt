package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.component.ProjectsSessionsComponent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.archive_named_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.archive_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_archive_chat_icon
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_project_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_actions
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_in_project
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraSessionList(
    state: AiStudioScreenState,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    projectsSessions: ProjectsSessionsComponent,
    modifier: Modifier = Modifier,
) {
    val chats = state.chats
    val byId = if (state.usesPagedNavigation) chats.associateBy(Chat::id) else mapOf()
    val projectTitles = state.navigation
        .filter { it.kind == NavigationKind.Project || it.kind == NavigationKind.Section }
        .associate { it.project to it.title }
    val list = rememberLazyListState()
    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(AuroraLayout.FrameStroke)) {
        if (state.usesPagedNavigation) {
            items(state.navigation, key = NavigationRow::id) { row ->
                if (row.kind == NavigationKind.Chat) {
                    val chat = byId[row.id] ?: Chat(
                        row.id, row.title, projectTitles[row.project].orEmpty(), projectId = row.project,
                    )
                    AuroraChatRow(chat, row.selected, touch, onAction)
                } else if (row.kind.isPagination) {
                    AuroraNavigationPage(row, list, touch, projectsSessions)
                } else {
                    val title = row.displayTitle()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AuroraControl(
                            { projectsSessions.setProjectExpanded(row.project, row.expanded != true) },
                            Modifier.weight(1f), touch = touch, background = Color.Transparent, round = false,
                        ) {
                            AuroraText(title)
                        }
                        AuroraControl(
                            { onAction(AiStudioAction.CreateSessionInProject(row.project)) },
                            label = stringResource(Res.string.new_project_session, title),
                            touch = touch, background = Color.Transparent,
                        ) {
                            HbIcon(HbIconGlyph.Edit, tint = AuroraColors.NavigationIcon, size = AuroraLayout.Glyph)
                        }
                    }
                }
            }
        } else {
            items(chats, key = Chat::id) { chat ->
                AuroraChatRow(chat, selected = state.chat?.id == chat.id, touch = touch, onAction = onAction)
            }
        }
    }
}

internal fun NavigationRow.loadNextPage(projectsSessions: ProjectsSessionsComponent) {
    when (kind) {
        NavigationKind.MoreProjects -> projectsSessions.loadMoreProjects()
        NavigationKind.More -> projectsSessions.loadMoreSessions(project)
        NavigationKind.MoreChildren -> projectsSessions.loadMoreChildren(project)
        else -> error("Expected pagination row, got $kind")
    }
}

@Composable
private fun AuroraChatRow(chat: Chat, selected: Boolean, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    val title = chat.displayTitle()
    val project = chat.displayProject()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AuroraControl(
            { onAction(AiStudioAction.OpenChat(chat.id)) },
            Modifier.weight(1f),
            stringResource(Res.string.session_in_project, title, project),
            touch,
            background = if (selected) AuroraColors.Lavender else Color.Transparent,
            round = false,
        ) {
            AiStudioIcon(AiStudioGlyph.Chat, Modifier.size(AuroraLayout.Glyph), AuroraColors.Muted)
            Column(
                Modifier.padding(
                    start = AuroraLayout.Space,
                    top = AuroraLayout.SmallGap,
                    bottom = AuroraLayout.SmallGap,
                ),
            ) {
                AuroraText(title, lines = 1)
                AuroraText(project, small = true, color = AuroraColors.Muted, lines = 1)
            }
        }
        AuroraSessionMenu(chat, touch, onAction)
    }
}

@Composable
internal fun AuroraSessionActions(
    row: NavigationRow,
    archiveVisible: Boolean,
    onAction: (AiStudioAction) -> Unit,
    rowHeight: Dp,
) {
    if (row.kind != NavigationKind.Chat) return
    Box(
        Modifier.width(AuroraLayout.NavigationSearchTarget).height(rowHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (archiveVisible) {
            SidebarAction(
                stringResource(Res.string.archive_named_session, row.displayTitle()),
                { onAction(AiStudioAction.ArchiveChat(row.id)) },
                Modifier.fillMaxWidth(),
                rowHeight = rowHeight,
            ) {
                Image(
                    painterResource(Res.drawable.aurora_archive_chat_icon), null, Modifier.size(AuroraLayout.Glyph),
                    colorFilter = ColorFilter.tint(AuroraColors.NavigationIcon),
                )
            }
        } else {
            row.status?.let { SidebarStatus(it) }
        }
    }
}

@Composable
private fun AuroraSessionMenu(chat: Chat, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    val target = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget
    val actions = listOf(
        HbMenuAction(stringResource(Res.string.archive_session), { onAction(AiStudioAction.ArchiveChat(chat.id)) }),
    )
    AuroraMenu(actions) { modifier, open ->
        AuroraControl(
            open,
            modifier,
            label = stringResource(Res.string.session_actions, chat.displayTitle()),
            touch = touch,
            background = Color.Transparent,
            horizontalPadding = (target - AuroraLayout.Glyph) / 2,
        ) {
            HbIcon(HbIconGlyph.More, tint = AuroraColors.Muted, size = AuroraLayout.Glyph)
        }
    }
}
