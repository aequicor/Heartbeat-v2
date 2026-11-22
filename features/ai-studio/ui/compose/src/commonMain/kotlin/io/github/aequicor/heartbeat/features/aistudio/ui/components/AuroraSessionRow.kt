package io.github.aequicor.heartbeat.features.aistudio.ui.components

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
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.archive_named_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.archive_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_archive_chat_icon
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
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(AuroraLayout.FrameStroke)) {
        if (state.usesPagedNavigation) {
            items(state.navigation, key = NavigationRow::id) { row ->
                if (row.kind == NavigationKind.Chat) {
                    byId[row.id]?.let { AuroraChatRow(it, row.selected, touch, onAction) }
                } else {
                    val onClick: () -> Unit = if (row.kind.isPagination) {
                        { row.loadNextPage(projectsSessions) }
                    } else {
                        { projectsSessions.setProjectExpanded(row.project, row.expanded != true) }
                    }
                    AuroraControl(
                        onClick,
                        Modifier.fillMaxWidth(),
                        touch = touch,
                        background = Color.Transparent,
                        round = false,
                    ) {
                        AuroraText(row.displayTitle())
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
internal fun AuroraSessionActions(row: NavigationRow, archiveVisible: Boolean, onAction: (AiStudioAction) -> Unit) {
    if (row.kind != NavigationKind.Chat) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (archiveVisible) {
            SidebarAction(
                stringResource(Res.string.archive_named_session, row.displayTitle()),
                { onAction(AiStudioAction.ArchiveChat(row.id)) },
                Modifier.width(AuroraLayout.DesktopTarget),
            ) {
                Image(
                    painterResource(Res.drawable.aurora_archive_chat_icon), null, Modifier.size(AuroraLayout.Glyph),
                    colorFilter = ColorFilter.tint(AuroraColors.NavigationIcon),
                )
            }
        }
        val status = row.status
        if (status != null) SidebarStatus(status, Modifier.width(AuroraLayout.DesktopTarget))
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
