package io.github.aequicor.heartbeat.features.aistudio.presentation.state

import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.WorkEventKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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

    fun insert(chat: AuroraChat): AuroraWorkspace {
        val project = chat.summary.project
        val ordered = chats.toMutableList()
        val index = ordered.indexOfFirst { it.summary.project == project }.coerceAtLeast(0)
        ordered.add(index, chat)
        return copy(chats = ordered, collapsedNavigation = collapsedNavigation - "project:$project")
    }

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
    val mockId: String? = null,
    @Transient val payloadLoaded: Boolean = true,
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

internal val Chat.workState: SessionWorkState
    get() = when (navigationStatus) {
        null -> SessionWorkState.Idle
        NavigationStatus.Unread -> SessionWorkState.Waiting
        NavigationStatus.Working -> SessionWorkState.Working
        NavigationStatus.Attention -> SessionWorkState.Demand
    }

internal fun SessionWorkState.toNavigationStatus(): NavigationStatus? = when (this) {
    SessionWorkState.Idle -> null
    SessionWorkState.Waiting -> NavigationStatus.Unread
    SessionWorkState.Working -> NavigationStatus.Working
    SessionWorkState.Demand -> NavigationStatus.Attention
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
