package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus

internal suspend fun File.withContent(storage: StudioWorkspaceStorage?): File =
    if (storedAttachmentId != null && storage != null) copy(content = storage.openFile(storedAttachmentId).content) else this

private suspend fun Reply.withEventBodies(
    storage: StudioWorkspaceStorage,
    keys: Set<String>,
): Reply {
    if (keys.isEmpty()) return this
    val hydrated = steps.map { step ->
        val events = step.events.map { event ->
            if (event.storedBodyId != null && "$id/${step.id}/${event.id}" in keys) {
                event.copy(details = storage.readPartBody(event.storedBodyId), storedBodyId = null)
            } else event
        }
        if (events == step.events) step else step.copy(events = events)
    }
    return if (hydrated == steps) this else copy(steps = hydrated)
}


internal suspend fun MutableStateFlow<AiStudioScreenState>.toggleStoredEvent(
    chat: AuroraChat,
    action: AiStudioAction.ToggleWorkEvent,
    storage: StudioWorkspaceStorage?,
    expanded: MutableMap<Pair<String, String>, Pair<Reply, Reply>>,
) {
    val key = "${action.replyId}/${action.stepId}/${action.eventId}"
    val source = chat.messages.find { it.id == action.replyId } as? Reply
    val opening = key !in value.expandedEvents
    if (opening && source != null && storage != null) {
        val reply = value.messages.find { it.id == action.replyId } as? Reply ?: source
        val displayed = readStored { reply.withEventBodies(storage, setOf(key)) } ?: return
        expanded[chat.summary.id to reply.id] = source to displayed
        update { state ->
            state.copy(messages = state.messages.map { if (it === reply) displayed else it })
        }
    }
    update { it.copy(expandedEvents = if (opening) it.expandedEvents + key else it.expandedEvents - key) }
}

internal suspend fun MutableStateFlow<AiStudioScreenState>.setWorkExpanded(
    chat: AuroraChat,
    action: AiStudioAction.SetWorkExpanded,
    storage: StudioWorkspaceStorage?,
    expanded: MutableMap<Pair<String, String>, Pair<Reply, Reply>>,
) {
    val replyId = action.replyId
    val reply = (value.messages.firstOrNull { it.id == replyId } as? Reply)
        ?: value.live?.takeIf { it.id == replyId } ?: return
    val whole = action.stepId == null
    if (whole && reply.status == ReplyStatus.Working) return
    val steps = reply.steps.filter { whole || it.id == action.stepId }
    val stepKeys = if (action.eventId != null) emptySet() else steps.filter { it.status != StepStatus.Current }
        .mapTo(mutableSetOf()) { "$replyId/${it.id}" }
    val eventKeys = steps.flatMapTo(mutableSetOf()) { step ->
        step.events.filter { action.eventId == null || it.id == action.eventId }.map { "$replyId/${step.id}/${it.id}" }
    }
    val displayed = if (action.expanded && storage != null && reply !== value.live) {
        readStored { reply.withEventBodies(storage, eventKeys - value.expandedEvents) } ?: return
    } else reply
    val source = chat.messages.firstOrNull { it.id == replyId } as? Reply
    if (source != null && displayed !== reply) expanded[chat.summary.id to replyId] = source to displayed
    update { state ->
        state.copy(
            messages = if (displayed === reply) state.messages else state.messages.map { if (it === reply) displayed else it },
            expandedWork = when {
                !whole -> state.expandedWork
                action.expanded -> state.expandedWork + replyId
                else -> state.expandedWork - replyId
            },
            expandedSteps = if (action.expanded) state.expandedSteps + stepKeys else state.expandedSteps - stepKeys,
            expandedEvents = if (action.expanded) state.expandedEvents + eventKeys else state.expandedEvents - eventKeys,
        )
    }
}

internal suspend fun <T> MutableStateFlow<AiStudioScreenState>.readStored(read: suspend () -> T): T? = try {
    read()
} catch (_: StudioRepositoryException) {
    update { it.copy(loading = false, storageError = "Не удалось прочитать историю чата") }
    null
}
