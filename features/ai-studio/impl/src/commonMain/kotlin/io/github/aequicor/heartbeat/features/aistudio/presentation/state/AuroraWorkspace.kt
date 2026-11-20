package io.github.aequicor.heartbeat.features.aistudio.presentation.state

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import kotlinx.serialization.Serializable

@Serializable
internal data class AuroraWorkspace(
    val selectedId: String,
    val chats: List<AuroraChat>,
    val serial: Long = 0,
    val collapsedNavigation: Set<String> = setOf(),
    val expandedProjects: Set<String> = setOf(),
    val archivedChats: Set<String> = setOf(),
) {
    fun recover(): AuroraWorkspace = copy(chats = chats.map(AuroraChat::recover))

    fun activeChats(): Sequence<Chat> = chats.asSequence().map(AuroraChat::summary).filterNot { it.id in archivedChats }
}

@Serializable
internal data class AuroraChat(
    val summary: Chat,
    val draft: String = "",
    val anchor: Anchor = Anchor(),
    val attachments: List<File> = listOf(),
    val messages: List<Message> = listOf(),
    val interrupted: Reply? = null,
) {
    fun recover(): AuroraChat {
        val history = messages.map { message ->
            when (message) {
                is User -> if (message.delivery == Delivery.Sending) {
                    message.copy(delivery = Delivery.Failed, error = "Отправка прервана при закрытии приложения")
                } else message
                is Reply -> message
            }
        }
        val stopped = interrupted?.finishCurrentSteps(StepStatus.Stopped)?.copy(status = ReplyStatus.Stopped)
        return copy(messages = if (stopped == null) history else listOf(stopped) + history, interrupted = null)
    }
}

internal fun Reply.finishCurrentSteps(status: StepStatus): Reply = copy(
    steps = steps.map { step ->
        if (step.status != StepStatus.Current) step
        else step.copy(status = status, events = step.events.map { it.finish(status) })
    },
)

private fun WorkEvent.finish(finalStatus: StepStatus): WorkEvent = when {
    status != StepStatus.Current -> this
    kind == WorkEventKind.Thinking -> copy(
        status = finalStatus,
        title = if (finalStatus == StepStatus.Done) "Размышления" else "Размышления прерваны",
    )
    else -> copy(status = finalStatus, summary = finalStatus.label)
}
