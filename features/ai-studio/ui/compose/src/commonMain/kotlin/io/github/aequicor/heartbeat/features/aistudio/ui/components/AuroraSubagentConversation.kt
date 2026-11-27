package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SubagentConversation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Request
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User

@Composable
internal fun AuroraSubagentConversation(
    conversation: SubagentConversation,
    touch: Boolean,
    preferredWidth: Dp,
    captionClearance: Dp,
    onAction: (AiStudioAction) -> Unit,
    request: Request? = null,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.widthIn(max = preferredWidth).fillMaxWidth().fillMaxHeight()
                .padding(
                    top = captionClearance, start = AuroraLayout.ContentInset, end = AuroraLayout.ContentInset,
                    bottom = AuroraLayout.ContentInset,
                ),
            verticalArrangement = Arrangement.spacedBy(AuroraLayout.ContentInset),
        ) {
            AuroraChip("К родительскому чату", { onAction(AiStudioAction.CloseSubagentConversation) }, touch = touch)
            AuroraText(conversation.title)
            val history = conversation.messages
            if (history != null && history.isNotEmpty()) {
                AuroraSubagentHistory(history, touch, Modifier.weight(1f))
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
                ) {
                    if (conversation.loading) {
                        AuroraText("Загрузка истории…", small = true, color = AuroraColors.Muted)
                    } else {
                        if (history != null) AuroraText("История пока пуста", small = true, color = AuroraColors.Muted)
                        conversation.status?.let { AuroraText(it.label, small = true, color = AuroraColors.Muted) }
                        if (conversation.output.isNotBlank()) {
                            SelectionContainer { AuroraText(conversation.output) }
                        }
                    }
                }
            }
            if (request != null) AuroraRequestPanel(request, touch, onAction, inlineStop = false)
            AuroraReadOnlyComposer(touch)
        }
    }
}


@Composable
private fun AuroraSubagentHistory(messages: List<Message>, touch: Boolean, modifier: Modifier = Modifier) {
    var expandedWork by remember { mutableStateOf(setOf<String>()) }
    var expandedSteps by remember { mutableStateOf(setOf<String>()) }
    var expandedEvents by remember { mutableStateOf(setOf<String>()) }
    val handle: (AiStudioAction) -> Unit = { action ->
        when (action) {
            is AiStudioAction.ToggleWork -> expandedWork = expandedWork.toggleEntry(action.id)
            is AiStudioAction.ToggleStep -> expandedSteps = expandedSteps.toggleEntry("${action.replyId}/${action.stepId}")
            is AiStudioAction.ToggleWorkEvent -> expandedEvents =
                expandedEvents.toggleEntry("${action.replyId}/${action.stepId}/${action.eventId}")
            else -> Unit
        }
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
    ) {
        items(messages.asReversed(), key = Message::id) { message ->
            val selection = rememberSelectionState()
            when (message) {
                is User -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    AuroraUserBubble(message, touch, handle, selection)
                }
                is Reply -> AuroraAgentMessage(
                    message, message.id in expandedWork, expandedSteps, expandedEvents, touch, handle,
                    selection = selection,
                )
            }
        }
    }
}

private fun Set<String>.toggleEntry(key: String): Set<String> = if (key in this) this - key else this + key
