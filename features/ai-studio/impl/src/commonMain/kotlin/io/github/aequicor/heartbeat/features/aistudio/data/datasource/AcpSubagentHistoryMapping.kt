package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSubagents
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ReadSubagentHistoryUseCase.Subagent

internal fun SessionSubagents.Subagent.toChatSubagent(): Subagent {
    val running = activity == SessionSubagents.Activity.Running || activity == SessionSubagents.Activity.RequiresAction
    val messages = mutableListOf<ChatHistoryEntry.Message>()
    var segmentStart = 0

    fun addReply(end: Int, isRunning: Boolean) {
        if (segmentStart == end) return
        val segment = items.subList(segmentStart, end)
        val turn = segment.toChatTurn(if (isRunning) ChatEngineTurn.Status.Running else ChatEngineTurn.Status.Completed)
        if (turn.text.isNotEmpty() || turn.interim != null || turn.steps.isNotEmpty()) {
            messages.add(ChatHistoryEntry.Reply(
                id = "reply-${segment.first().id}", time = "", text = turn.text, promptId = null,
                interim = turn.interim,
                status = if (isRunning) ChatHistoryEntry.ReplyStatus.Working else ChatHistoryEntry.ReplyStatus.Completed,
                steps = turn.steps, result = null, retryAllowed = false,
            ))
        }
    }

    items.forEachIndexed { index, item ->
        if (item is SessionTimelineItem.UserMessage) {
            addReply(index, isRunning = false)
            messages.add(ChatHistoryEntry.User(
                id = item.id, time = "", text = item.content.joinToString("") { it.display() }, promptId = null,
                files = emptyList(), delivery = ChatHistoryEntry.Delivery.Sent, error = null, answers = false,
                scenario = ChatHistoryEntry.Scenario.Standard, access = ChatHistoryEntry.Access.ReadOnly, retryAllowed = false,
            ))
            segmentStart = index + 1
        }
    }
    addReply(items.size, running)

    val status = when (activity) {
        SessionSubagents.Activity.Running, SessionSubagents.Activity.RequiresAction -> ChatHistoryEntry.StepStatus.Current
        SessionSubagents.Activity.Idle -> ChatHistoryEntry.StepStatus.Done
        SessionSubagents.Activity.Unknown -> null
    }
    return Subagent(id, title, description, status, messages)
}
