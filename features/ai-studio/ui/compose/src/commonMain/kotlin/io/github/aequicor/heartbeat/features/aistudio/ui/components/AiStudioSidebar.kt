package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState

@Composable
internal fun AiStudioSidebar(
    state: AiStudioScreenState,
    onAction: (AiStudioAction) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    Column(
        modifier.fillMaxHeight().background(colors.surface.subtle).padding(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Чаты",
                modifier = Modifier.weight(1f),
                style = HbTypography.PaneTitle,
                color = { colors.text.primary },
            )
            if (onClose != null) {
                HbButton("Назад", onClose, variant = HbButtonVariant.Ghost)
            }
        }
        if (state.connected) {
            HbButton(
                "Новый чат",
                onClick = { onAction(AiStudioAction.NewChat) },
                enabled = state.canManageChats,
                modifier = Modifier.fillMaxWidth(),
                variant = HbButtonVariant.Secondary,
            )
            AiStudioSessionList(state, onAction, Modifier.weight(1f))
            HbButton(
                "Обновить",
                onClick = { onAction(AiStudioAction.RefreshSessions) },
                enabled = state.canManageChats,
                variant = HbButtonVariant.Ghost,
            )
        }
    }
}

@Composable
private fun AiStudioSessionList(
    state: AiStudioScreenState,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs)) {
        if (state.sessions.isEmpty()) {
            item {
                BasicText(
                    if (state.busy) "Загрузка…" else "В этом каталоге пока нет чатов",
                    style = HbTypography.Body,
                    color = { colors.text.secondary },
                )
            }
        }
        items(state.sessions, key = { it.id }) { chat ->
            AiStudioChatRow(
                chat = chat,
                selected = state.selected == chat,
                enabled = state.canManageChats,
                onClick = { onAction(AiStudioAction.OpenChat(chat)) },
            )
        }
        if (state.hasMoreSessions) {
            item {
                HbButton(
                    "Показать ещё",
                    onClick = { onAction(AiStudioAction.LoadMoreSessions) },
                    enabled = state.canManageChats,
                    variant = HbButtonVariant.Ghost,
                )
            }
        }
    }
}

@Composable
private fun AiStudioChatRow(chat: SessionKey, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = HbTheme.colors
    val background = if (selected) colors.selection.background else colors.surface.subtle
    val foreground = if (selected) colors.selection.foreground else colors.text.primary
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = HbSizes.controlHeight)
            .clip(HbShapes.Small)
            .background(background)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
    ) {
        BasicText(
            chat.title?.takeIf(String::isNotBlank) ?: "Чат ${chat.id.take(SessionIdLabelLength)}",
            style = HbTypography.Body,
            color = { foreground },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val SessionIdLabelLength = 8
