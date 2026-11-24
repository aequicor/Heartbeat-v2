package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatHistoryInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Access
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileDate
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyFailure
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Scenario
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ToolOperation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class WorkspaceHistory(
    private val metadata: StudioWorkspaceMetadata,
    private val history: ChatHistoryInteractor,
    private val defaultDispatcher: CoroutineDispatcher,
) : StudioWorkspaceStorage, StudioWorkspaceMetadata by metadata {

    override suspend fun load(chat: AuroraChat): AuroraChat {
        val saved = metadata.loadMetadata(chat)
        if (chat.payloadLoaded && saved === chat) return chat
        val page = withContext(defaultDispatcher) { history.page(chat.summary.id, anchor = saved.anchor.messageId) }.getOrThrow()
        return saved.copy(messages = emptyList(), historyAtLoad = emptyList()).withHistory(page)
    }

    override suspend fun loadOlder(chat: AuroraChat): AuroraChat {
        val cursor = chat.olderBefore ?: return chat
        val page = withContext(defaultDispatcher) { history.page(chat.summary.id, cursor) }.getOrThrow()
        return chat.withHistory(page)
    }

    override suspend fun listFiles(sessionId: String): List<File> =
        withContext(defaultDispatcher) { history.files(sessionId).getOrThrow().map(ChatAttachment::display) }

    override suspend fun openFile(attachmentId: String): File =
        withContext(defaultDispatcher) { history.file(attachmentId).getOrThrow().display() }

    override suspend fun readPartBody(partId: Long): String =
        withContext(defaultDispatcher) { history.partBody(partId).getOrThrow() }
}

private fun AuroraChat.withHistory(page: ChatHistoryEntry.Page): AuroraChat {
    val displayed = page.entries.map { it.recoveredMessage.display() }
    // A workspace metadata save must not commit a display status for a draft another process can still own.
    return copy(messages = messages + displayed, historyAtLoad = historyAtLoad + displayed, olderBefore = page.before)
}

internal fun ChatAttachment.display() = File(id, name, kind, content,
    date?.let { FileDate(it.year, it.month, it.day, it.time) }, storageId, size,
    image?.let { ImageAttachment(it.mimeType, it.thumbnail) }, mimeType, base64Encoded)

internal fun ChatHistoryEntry.Message.display(): Message = when (this) {
    is ChatHistoryEntry.User -> User(id, time, text, files.map(ChatAttachment::display), Delivery.valueOf(delivery.name),
        error, answers, Scenario.valueOf(scenario.name), promptId, Access.valueOf(access.name), retryAllowed)
    is ChatHistoryEntry.Reply -> Reply(id, time, steps.map { step ->
        Step(step.id, step.title, step.details, StepStatus.valueOf(step.status.name), step.events.map { it.display() },
            step.result, step.activeTitle)
    }, interim, text, ReplyStatus.valueOf(status.name), result?.display(), promptId, retryAllowed,
        failure = when (failure) {
            ChatHistoryEntry.ReplyFailure.RateLimited -> ReplyFailure.RateLimited
            null -> null
        },
        artifacts = artifacts.map(ChatAttachment::display),
    )
}

private fun ChatHistoryEntry.Event.display(): WorkEvent {
    val event = WorkEvent(id, title, summary, details, WorkEventKind.valueOf(kind.name), StepStatus.valueOf(status.name),
        detailLabel, storedBodyId = bodyId, operation = operation?.let { ToolOperation.valueOf(it.name) }, command = command,
        files = files.map(ChatAttachment::display))
    return detailsFormat?.let { event.copy(detailsFormat = DetailFormat.valueOf(it.name)) } ?: event
}
