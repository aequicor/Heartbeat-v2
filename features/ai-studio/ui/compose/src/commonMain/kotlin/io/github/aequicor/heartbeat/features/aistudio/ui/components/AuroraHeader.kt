package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuEntry
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Subagent
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.projects_sessions
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_in_project
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraHeader(
    state: AiStudioScreenState,
    touch: Boolean,
    showNavigation: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
    captionClearance: Dp = Dp.Hairline,
    captionOnLeft: Boolean = false,
    edgeInset: Dp = Dp.Hairline,
    onDesktopTitleReady: (FocusRequester) -> Unit = {},
) {
    val chat = state.chat ?: return
    if (touch) {
        AuroraTouchHeader(state, chat, showNavigation, onAction, modifier)
    } else {
        AuroraDesktopHeader(
            state, chat, showNavigation, onAction, captionClearance, captionOnLeft, edgeInset,
            onDesktopTitleReady, modifier,
        )
    }
}

@Composable
private fun AuroraTouchHeader(
    state: AiStudioScreenState,
    chat: Chat,
    showNavigation: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = AuroraLayout.Header).fillMaxWidth()
            .padding(vertical = AuroraLayout.Space),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.HeaderRowGap),
    ) {
        val inset = Modifier.padding(horizontal = AuroraLayout.ContentInset)
        AuroraTouchChatTitle(chat, showNavigation, state.subagents, onAction, inset)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraTouchParameters(chat, onAction, Modifier.weight(1f))
            AuroraTouchMetrics(chat.contextPercent, chat.quota, Modifier.padding(end = AuroraLayout.ContentInset))
        }
        AuroraHeaderStorageError(state.navigationErrorText(), inset)
    }
}

@Composable
private fun AuroraTouchChatTitle(
    chat: Chat, showNavigation: Boolean, agents: List<Subagent>, onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNavigation) {
            AuroraTouchHeaderIcon(
                stringResource(Res.string.projects_sessions),
                { onAction(AiStudioAction.ExpandChats) },
            ) {
                AiStudioIcon(AiStudioGlyph.Menu, Modifier.size(AuroraLayout.Glyph), AuroraColors.Muted)
            }
        }
        val actions = listOf(
            HbMenuAction(stringResource(Res.string.new_session), { onAction(AiStudioAction.NewChat) }),
            HbMenuAction("Файлы диалога", { onAction(AiStudioAction.ToggleFiles(true)) }),
        )
        val radius = AuroraLayout.MessageRadius
        val title = chat.displayTitle()
        val project = chat.displayProject()
        AuroraHeaderMenu(actions, Modifier.weight(1f), touch = true) { trigger, open ->
            AuroraHeaderButton(
                stringResource(Res.string.session_in_project, title, project),
                open,
                trigger.fillMaxWidth().auroraHeaderSurface(radius, outline = false),
                height = AuroraLayout.TouchTarget, radius = radius,
                contentPadding = PaddingValues(horizontal = AuroraLayout.Space),
            ) {
                Column {
                    AuroraHeaderText(project, color = AuroraColors.Muted)
                    AuroraHeaderText(title, style = HbTypography.PaneTitle)
                }
            }
        }
        if (agents.isNotEmpty()) AuroraTouchSubagents(agents, onAction)
    }
}

@Composable
private fun AuroraTouchSubagents(agents: List<Subagent>, onAction: (AiStudioAction) -> Unit) {
    val actions = agents.map { HbMenuAction(it.title, { onAction(AiStudioAction.OpenChat(it.chatId)) }) }
    AuroraHeaderMenu(actions, alignEnd = true, touch = true) { trigger, open ->
        AuroraTouchHeaderIcon("Субагенты · ${agents.size}", open, trigger) {
            HbIcon(HbIconGlyph.Branch, tint = AuroraColors.Muted, size = AuroraLayout.Glyph)
        }
    }
}

@Composable
private fun AuroraTouchHeaderIcon(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val radius = AuroraLayout.TouchTarget / 2
    AuroraHeaderButton(
        label, onClick,
        modifier.size(AuroraLayout.TouchTarget).auroraHeaderSurface(radius, outline = false),
        height = AuroraLayout.TouchTarget, radius = radius,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun AuroraTouchParameters(
    chat: Chat,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true),
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.HeaderClusterGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.auroraHeaderSurface(AuroraLayout.MessageRadius, outline = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraTouchParameter(
                AuroraDemoParameters.Engine, "Демонстрационный движок",
                AuroraDemoParameters.engineOptions(chat.scenario, onAction),
            )
            AuroraTouchParameter(AuroraDemoParameters.Model, "Демонстрационная модель", listOf(
                HbMenuAction(AuroraDemoParameters.Model, {}, selected = true),
            ))
            AuroraTouchParameter(
                AuroraDemoParameters.effortLabel(chat.effort), "Уровень рассуждения",
                AuroraDemoParameters.effortOptions(chat.effort, onAction),
            )
        }
        AuroraAccessMenu(chat.access, onAction, touch = true)
    }
}

@Composable
private fun AuroraTouchParameter(value: String, label: String, actions: List<HbMenuEntry>) {
    AuroraHeaderMenu(actions, touch = true) { trigger, open ->
        AuroraHeaderButton(
            "$label, $value", open, trigger, height = AuroraLayout.TouchTarget,
            radius = AuroraLayout.MessageRadius, contentPadding = PaddingValues(horizontal = AuroraLayout.Space),
        ) {
            AuroraHeaderText(value, Modifier.widthIn(max = AuroraLayout.ParameterMaxWidth))
            HbIcon(
                HbIconGlyph.ChevronDown, Modifier.padding(start = AuroraLayout.SmallGap),
                tint = AuroraColors.Muted, size = AuroraLayout.SmallGlyph,
            )
        }
    }
}

@Composable
internal fun AuroraHeaderStorageError(problem: String?, modifier: Modifier = Modifier) {
    if (problem != null) {
        AuroraText(
            problem, modifier.background(AuroraColors.Pearl, RoundedCornerShape(AuroraLayout.SmallRadius))
                .padding(AuroraLayout.Space),
            color = AuroraColors.Error, small = true,
        )
    }
}
