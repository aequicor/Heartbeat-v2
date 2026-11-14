package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbScrollArea
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aiengine.api.session.PendingPermission
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolOutput
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState

@Composable
internal fun AiStudioTranscript(
    state: AiStudioScreenState,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    val items = state.timeline
    val pending = (state.sessionStatus as? SessionStatus.AwaitingPermission)?.requests.orEmpty()
    val scroll = rememberScrollState()
    var previousEnd by remember { mutableIntStateOf(0) }
    LaunchedEffect(scroll.maxValue) {
        val end = scroll.maxValue
        if (end != Int.MAX_VALUE) {
            val follows = scroll.value >= previousEnd
            previousEnd = end
            if (follows) scroll.scrollTo(end)
        }
    }

    HbScrollArea(modifier, state = scroll) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = HbPaneGeometry.ReadingMax.dp).fillMaxWidth().padding(HbSpacing.M),
                verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
            ) {
                if (items.isEmpty() && pending.isEmpty()) {
                    BasicText(state.emptyChatLabel(), style = HbTypography.Body, color = { colors.text.secondary })
                }
                items.forEach { item ->
                    AiStudioTimelineItem(item)
                    pending.filter { it.toolCallEntryId == item.id }.forEach { AiStudioPermission(it, onAction) }
                }
                pending.filter { request -> items.none { it.id == request.toolCallEntryId } }.forEach {
                    AiStudioPermission(it, onAction)
                }
            }
        }
    }
}

@Composable
private fun AiStudioTimelineItem(item: SessionTimelineItem) {
    when (item) {
        is SessionTimelineItem.UserMessage -> AiStudioMessage("Вы", item.content, user = true)
        is SessionTimelineItem.AgentMessage -> AiStudioMessage("DeepSeek Harness", item.content)
        is SessionTimelineItem.ToolCall -> AiStudioTool(item)
        is SessionTimelineItem.ContextCompaction -> AiStudioMessage("Сжатие контекста", item.summary.orEmpty())
    }
}

@Composable
private fun AiStudioMessage(label: String, content: List<SessionContent>, user: Boolean = false) {
    val colors = HbTheme.colors
    val surface = if (user) colors.surface.subtle else colors.surface.base
    Column(
        Modifier.fillMaxWidth().clip(HbShapes.Medium).background(surface).padding(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        BasicText(label, style = HbTypography.Label, color = { colors.text.secondary })
        SelectionContainer {
            Column {
                content.forEach {
                    BasicText(it.displayText(), style = HbTypography.Body, color = { colors.text.primary })
                }
            }
        }
    }
}

@Composable
private fun AiStudioTool(tool: SessionTimelineItem.ToolCall) {
    val colors = HbTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(HbShapes.Medium).background(colors.surface.subtle).padding(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        BasicText(tool.title, style = HbTypography.Label, color = { colors.text.primary })
        BasicText(tool.status.label(), style = HbTypography.Caption, color = { colors.text.secondary })
        SelectionContainer {
            Column {
                tool.output.forEach {
                    BasicText(it.displayText(), style = HbTypography.Code, color = { colors.text.primary })
                }
            }
        }
    }
}

@Composable
private fun AiStudioPermission(request: PendingPermission, onAction: (AiStudioAction) -> Unit) {
    val colors = HbTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(HbShapes.Medium).background(colors.status.warning.container).padding(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        BasicText("Нужно разрешение", style = HbTypography.Label, color = { colors.status.warning.foreground })
        BasicText(request.description, style = HbTypography.Body, color = { colors.text.primary })
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        ) {
            request.options.forEach { option ->
                HbButton(
                    text = option.label,
                    onClick = { onAction(AiStudioAction.AnswerPermission(request.requestId, option.id)) },
                    variant = HbButtonVariant.Secondary,
                )
            }
        }
    }
}

private fun AiStudioScreenState.emptyChatLabel(): String = when {
    !engineAvailable -> "DeepSeek Harness доступен в настольном приложении"
    sessionStatus != null -> "Напишите первое сообщение в этом чате"
    connected -> "Создайте чат или выберите сессию из списка"
    else -> "Укажите рабочий каталог, чтобы начать чат"
}

private fun SessionContent.displayText(): String = when (this) {
    is SessionContent.Text -> text
    is SessionContent.Resource -> title ?: uri.takeUnless { it.startsWith("data:") } ?: mimeType.orEmpty()
}

private fun ToolOutput.displayText(): String = when (this) {
    is ToolOutput.Content -> value.displayText()
    is ToolOutput.Diff -> path
    is ToolOutput.Terminal -> id
}

private fun ToolCallStatus.label(): String = when (this) {
    ToolCallStatus.PENDING -> "Ожидает запуска"
    ToolCallStatus.IN_PROGRESS -> "Выполняется"
    ToolCallStatus.COMPLETED -> "Завершён"
    ToolCallStatus.FAILED -> "Ошибка"
    ToolCallStatus.CANCELLED -> "Отменён"
}
