package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import io.github.aequicor.heartbeat.features.aistudio.data.dao.applyEdit
import io.github.aequicor.heartbeat.features.aistudio.data.dao.draftOwner
import io.github.aequicor.heartbeat.features.aistudio.data.dao.historyUsesPreview
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatRevisionChange
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraChat
import kotlin.time.Clock

internal fun ChatQueries.saveHistory(chatId: String, chat: AuroraChat, previous: AuroraChat?) {
    if (previous?.messages === chat.messages && previous.interrupted === chat.interrupted) return
    val old = chat.historyAtLoad.associateByTo(mutableMapOf(), Message::id)
    previous?.messages?.associateByTo(old, Message::id)
    val interrupted = previous?.interrupted
    if (interrupted != null) old[interrupted.id] = interrupted
    for (message in chat.messages.asReversed() + listOfNotNull(chat.interrupted)) {
        val prior = old[message.id]
        if (message !== prior && message != prior) saveMessage(chatId, message, prior)
    }
}

private fun ChatQueries.saveMessage(chatId: String, message: Message, previous: Message?) {
    val current = selectMessage(chatId, message.id).executeAsOneOrNull()
    if (current?.status == "draft" && current.presentation.draftOwner() != null) return
    val header = message.header()
    val status = message.revisionStatus()
    val now = Clock.System.now().toEpochMilliseconds()
    val change = ChatRevisionChange.decide(current?.status, previous != null,
        current?.status == "draft" && sharedRevision(current.revision_id, chatId).executeAsOne())
    if (current == null) {
        if (change == ChatRevisionChange.Append) {
            insertFirstRevision(if (message is User) "user" else "assistant", status, now, message.id, header, chatId)
            val revision = lastInsertedId().executeAsOne()
            appendEntry(chatId, revision)
            writeParts(revision, message)
        }
        return
    }
    val saved = readMessage(current.revision_id, current.presentation, headersOnly = historyUsesPreview(current.status))
    if (saved == message) return
    if (previous != null && previous != saved) {
        check(previous == readMessage(current.revision_id, current.presentation)) {
            "The saved message changed in another workspace"
        }
    }
    if (change == ChatRevisionChange.UpdateDraft) {
        updateDraft(header, status, current.revision_id)
        writeParts(current.revision_id, message)
    } else if (change == ChatRevisionChange.Replace) {
        replaceRevision(chatId, current.position, current.revision_id, message, keepContinuation = true)
    }
}

private fun ChatQueries.replaceRevision(
    chatId: String, position: Long, parent: Long, message: Message, keepContinuation: Boolean,
) {
    insertEditedRevision(message.revisionStatus(), Clock.System.now().toEpochMilliseconds(), message.header(), parent)
    val revision = lastInsertedId().executeAsOne()
    writeParts(revision, message)
    applyEdit(chatId, position, revision, keepContinuation)
}
