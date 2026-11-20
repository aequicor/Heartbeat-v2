package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuEntry
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowClientArea
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Subagent
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_project_closed_icon
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraDesktopHeader(
    state: AiStudioScreenState,
    chat: Chat,
    showNavigation: Boolean,
    onAction: (AiStudioAction) -> Unit,
    captionClearance: Dp,
    captionOnLeft: Boolean,
    edgeInset: Dp,
    onTitleReady: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleFocus = remember { FocusRequester() }
    var subagentsFocused by remember { mutableStateOf(false) }
    val hasSubagents = state.subagents.isNotEmpty()
    val restoreTitleFocus = !hasSubagents && subagentsFocused
    SideEffect(hasSubagents) {
        if (restoreTitleFocus) titleFocus.requestFocus()
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth - edgeInset * 2 < AuroraLayout.HeaderMetricsCompactAt
        val inset = Modifier.padding(horizontal = edgeInset)
        Column(
            Modifier.fillMaxWidth().padding(top = AuroraLayout.HeaderTop),
            verticalArrangement = Arrangement.spacedBy(AuroraLayout.HeaderRowGap),
        ) {
            Row(
                Modifier.fillMaxWidth().then(inset).absolutePadding(
                    left = if (captionOnLeft) captionClearance else Dp.Hairline,
                    right = if (captionOnLeft) Dp.Hairline else captionClearance,
                ),
                horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showNavigation) AuroraHeaderNavigation(onAction)
                AuroraTitleMenu(
                    chat, state.filesVisible, onAction, titleFocus,
                    onReady = { onTitleReady(titleFocus) }, modifier = Modifier.weight(1f),
                )
                if (hasSubagents) {
                    AuroraSubagents(
                        state.subagents, onAction, onFocusChange = { subagentsFocused = it }, compact = compact,
                    )
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().then(inset),
                horizontalArrangement = Arrangement.spacedBy(AuroraLayout.HeaderClusterGap),
                verticalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
            ) {
                AuroraExecutionParameters(chat, onAction)
                AuroraAccessMenu(chat.access, onAction)
                if (chat.contextPercent != null || chat.quota != null) {
                    Spacer(Modifier.weight(1f))
                    AuroraHeaderMetrics(chat.contextPercent, chat.quota, compact)
                }
            }
            AuroraHeaderStorageError(state.storageError, inset)
        }
    }
}

@Composable
private fun AuroraHeaderNavigation(onAction: (AiStudioAction) -> Unit) {
    HbWindowClientArea { clientArea ->
        AuroraHeaderButton(
            "Диалоги", { onAction(AiStudioAction.ToggleChats(true)) },
            clientArea.auroraHeaderSurface(AuroraLayout.MessageRadius),
            radius = AuroraLayout.MessageRadius, contentPadding = PaddingValues(horizontal = AuroraLayout.Space),
        ) {
            AiStudioIcon(AiStudioGlyph.Panel, Modifier.size(AuroraLayout.Glyph), AuroraColors.Muted)
        }
    }
}

@Composable
private fun AuroraTitleMenu(
    chat: Chat,
    filesVisible: Boolean,
    onAction: (AiStudioAction) -> Unit,
    titleFocus: FocusRequester,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect(titleFocus) { onReady() }
    val actions = listOf(
        HbMenuAction("Новый диалог", { onAction(AiStudioAction.NewChat) }, glyph = HbIconGlyph.Edit),
        HbMenuAction(
            if (filesVisible) "Скрыть файлы диалога" else "Файлы диалога",
            { onAction(AiStudioAction.ToggleFiles(!filesVisible)) }, glyph = HbIconGlyph.FolderOpen,
        ),
    )
    var expanded by remember { mutableStateOf(false) }
    var truncated by remember(chat.title) { mutableStateOf(false) }
    HbWindowClientArea { clientArea ->
        HbMenu(
            expanded, onDismissRequest = { expanded = false }, actions = actions,
            modifier = modifier.then(clientArea), presentation = HbMenuPresentation.AuroraMessage,
        ) { trigger ->
            HbTooltip(chat.title, enabled = truncated && !expanded) {
                AuroraHeaderButton(
                    "Меню диалога «${chat.title}», проект ${chat.project}", { expanded = true },
                    trigger.focusRequester(titleFocus).fillMaxWidth().auroraFloatingHeaderSurface(AuroraColors.Pearl),
                    radius = AuroraLayout.FloatingHeaderRadius,
                    contentPadding = PaddingValues(horizontal = AuroraLayout.ContentInset),
                ) {
                    Image(
                        painterResource(Res.drawable.aurora_project_closed_icon), null,
                        Modifier.size(AuroraLayout.Glyph),
                        colorFilter = ColorFilter.tint(AuroraColors.NavigationIcon),
                    )
                    Spacer(Modifier.width(AuroraLayout.Space))
                    AuroraFadingText(
                        chat.title,
                        HbTypography.PaneTitle.copy(color = AuroraColors.MessageInk),
                        AuroraLayout.NavigationTitleFade, Modifier.weight(1f, fill = false),
                        onOverflowChange = { truncated = it },
                    )
                    Spacer(Modifier.width(AuroraLayout.Space))
                    HbIcon(HbIconGlyph.ChevronDown, tint = AuroraColors.MessageMuted, size = AuroraLayout.ContentInset)
                }
            }
        }
    }
}

@Composable
private fun AuroraSubagents(
    agents: List<Subagent>,
    onAction: (AiStudioAction) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    compact: Boolean,
) {
    val label = "Субагенты · ${agents.size}"
    val actions = agents.map { HbMenuAction(it.title, { onAction(AiStudioAction.OpenChat(it.chatId)) }) }
    HbWindowClientArea { clientArea ->
        AuroraHeaderMenu(actions, clientArea, alignEnd = true) { trigger, open ->
            AuroraHeaderButton(
                label, open,
                trigger.onFocusChanged { onFocusChange(it.isFocused) }
                    .auroraFloatingHeaderSurface(AuroraColors.Pearl, AuroraColors.HeaderOutline),
                radius = AuroraLayout.FloatingHeaderRadius,
                contentPadding = PaddingValues(horizontal = AuroraLayout.Space),
            ) {
                HbIcon(HbIconGlyph.Branch, tint = AuroraColors.MessageMuted, size = AuroraLayout.Glyph)
                if (!compact) {
                    Spacer(Modifier.width(AuroraLayout.ControlGap))
                    BasicText(
                        "Субагенты",
                        style = HbTypography.Label.copy(color = AuroraColors.MessageInk),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(AuroraLayout.SmallGap))
                    BasicText(
                        "· ${agents.size}",
                        style = HbTypography.Metadata.copy(color = AuroraColors.MessageMuted),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuroraExecutionParameters(
    chat: Chat,
    onAction: (AiStudioAction) -> Unit,
) {
    FlowRow(
        Modifier.auroraFloatingHeaderSurface(AuroraColors.Pearl, AuroraColors.HeaderOutline)
            .padding(
                horizontal = AuroraLayout.ContentInset,
                vertical = AuroraLayout.ExecutionParametersVerticalInset,
            ),
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.ExecutionParametersGap),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.ControlGap),
    ) {
        AuroraExecutionParameter(
            AuroraDemoParameters.Engine, "Демонстрационный движок",
            AuroraDemoParameters.engineOptions(chat.scenario, onAction),
        )
        AuroraExecutionParameter(AuroraDemoParameters.Model, "Демонстрационная модель", listOf(
            HbMenuAction(AuroraDemoParameters.Model, {}, selected = true),
        ))
        AuroraExecutionParameter(
            AuroraDemoParameters.effortLabel(chat.effort), "Уровень рассуждения",
            AuroraDemoParameters.effortOptions(chat.effort, onAction),
            displayValue = AuroraDemoParameters.effortSummary(chat.effort),
        )
    }
}

@Composable
private fun AuroraExecutionParameter(
    value: String,
    label: String,
    actions: List<HbMenuEntry>,
    displayValue: String = value,
) {
    AuroraHeaderMenu(actions) { trigger, open ->
        AuroraHeaderButton("$label, $value", open, trigger) {
            AuroraExecutionParameterValue(displayValue, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(AuroraLayout.ControlGap))
            HbIcon(HbIconGlyph.ChevronDown, tint = AuroraColors.MessageMuted, size = AuroraLayout.SmallGlyph)
        }
    }
}

@Composable
private fun AuroraExecutionParameterValue(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = AuroraColors.MessageInk,
) {
    BasicText(
        value, modifier,
        style = HbTypography.Label.copy(color = color),
    )
}

@Composable
internal fun AuroraAccessMenu(access: Access, onAction: (AiStudioAction) -> Unit, touch: Boolean = false) {
    val elevated = access == Access.Full || access == Access.Auto
    val color = when {
        elevated -> AuroraColors.Warning
        touch -> AuroraColors.Ink
        else -> AuroraColors.MessageInk
    }
    val shield = if (elevated) HbIconGlyph.ShieldAlert else HbIconGlyph.Shield
    val actions = Access.entries.map { mode ->
        HbMenuAction(mode.label, { onAction(AiStudioAction.SetAccess(mode)) }, selected = access == mode)
    }
    AuroraHeaderMenu(actions, touch = touch) { trigger, open ->
        val radius = if (touch) AuroraLayout.MessageRadius else AuroraLayout.FloatingHeaderRadius
        val height = if (touch) AuroraLayout.TouchTarget else AuroraLayout.HeaderButton
        val surface = if (touch) {
            trigger.auroraHeaderSurface(radius)
        } else {
            trigger.auroraFloatingHeaderSurface(AuroraColors.HeaderAccessSurface, AuroraColors.HeaderAccessOutline)
        }
        AuroraHeaderButton(
            "Режим доступа, ${access.label}", open, surface,
            height = height, radius = radius, contentPadding = PaddingValues(horizontal = AuroraLayout.ContentInset),
        ) {
            HbIcon(shield, tint = color, size = AuroraLayout.Glyph)
            Spacer(Modifier.width(AuroraLayout.Space))
            if (touch) {
                AuroraHeaderText(access.label, Modifier.widthIn(max = AuroraLayout.ParameterMaxWidth), color)
            } else {
                AuroraExecutionParameterValue(access.label, Modifier.weight(1f, fill = false), color)
            }
            Spacer(Modifier.width(AuroraLayout.Space))
            HbIcon(
                HbIconGlyph.ChevronDown, tint = if (touch) color else AuroraColors.MessageMuted,
                size = AuroraLayout.SmallGlyph,
            )
        }
    }
}
