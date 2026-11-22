package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply

internal suspend fun File.withContent(storage: StudioWorkspaceStorage?): File =
    if (storedAttachmentId != null && storage != null) storage.openFile(storedAttachmentId) else this

private suspend fun Reply.withEventBody(
    action: AiStudioAction.ToggleWorkEvent,
    storage: StudioWorkspaceStorage,
): Reply {
    val steps = steps.map { step ->
        if (step.id != action.stepId) step else step.copy(events = step.events.map { event ->
            if (event.id == action.eventId && event.storedBodyId != null) {
                event.copy(details = storage.readPartBody(event.storedBodyId), storedBodyId = null)
            } else event
        })
    }
    return copy(steps = steps)
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
        val displayed = readStored { reply.withEventBody(action, storage) } ?: return
        expanded[chat.summary.id to reply.id] = source to displayed
        update { state ->
            state.copy(messages = state.messages.map { if (it === reply) displayed else it })
        }
    }
    update { it.copy(expandedEvents = if (opening) it.expandedEvents + key else it.expandedEvents - key) }
}

internal suspend fun <T> MutableStateFlow<AiStudioScreenState>.readStored(read: suspend () -> T): T? = try {
    read()
} catch (_: StudioRepositoryException) {
    update { it.copy(loading = false, storageError = "Не удалось прочитать историю чата") }
    null
}
