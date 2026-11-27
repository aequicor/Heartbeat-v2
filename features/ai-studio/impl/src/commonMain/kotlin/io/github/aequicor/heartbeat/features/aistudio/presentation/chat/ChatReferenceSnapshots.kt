package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentTransferMode
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatMessageBody
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatMessageBody.Node
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceReference
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceSnapshot
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceSnapshot.Fragment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceSnapshot.Fragment.Format
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SentAttachmentMetadata
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SentMessageAttachment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraReferenceSelection.Kind

internal fun ChatHistoryEntry.snapshot(
    selection: AuroraReferenceSelection,
    agentName: String?,
): ChatSourceSnapshot? {
    val user = message as? ChatHistoryEntry.User
    val reply = message as? ChatHistoryEntry.Reply
    val step = reply?.steps?.firstOrNull { it.id == selection.stepId }
    val event = step?.events?.firstOrNull { it.id == selection.eventId }
    val replyFiles = reply?.artifacts.orEmpty() + listOfNotNull(reply?.result)
    val files = event?.files ?: user?.files ?: replyFiles

    val fragment = when (selection.kind) {
        Kind.Message -> Fragment(
            user?.messageBody?.originalReferenceText() ?: message.text,
            Format.Markdown,
        )
        Kind.FinalResult -> Fragment(reply?.text ?: return null, Format.Markdown)
        Kind.InterimResult -> Fragment(step?.result ?: reply?.interim ?: return null, Format.Markdown)
        Kind.Reasoning -> Fragment(
            event?.details ?: step?.details ?: return null,
            if (event?.detailsFormat == ChatHistoryEntry.DetailFormat.Markdown) Format.Markdown else Format.Plain,
        )
        Kind.ToolCall -> Fragment(
            event?.let { listOfNotNull(it.title, it.summary, it.command, it.details).filter(String::isNotBlank).distinct().joinToString("\n") }
                ?: return null,
            Format.Plain,
        )
        Kind.ToolResult -> Fragment(
            event?.details.orEmpty().ifEmpty { event?.command.orEmpty() }.takeIf { event != null } ?: return null,
            event?.detailsFormat?.let { Format.valueOf(it.name) } ?: Format.Plain,
        )
        Kind.Attachment -> null
    }

    val range = if (selection.range != null || !selection.selectedText.isNullOrEmpty()) {
        fragment?.text?.resolveRange(selection) ?: return null
    } else null
    val fragments = when {
        selection.kind == Kind.Message && reply != null && range == null -> reply.completeReferenceFragments()
        selection.kind == Kind.Message && user?.messageBody?.hasQuotes == true ->
            user.messageBody.referenceFragments(range)
        fragment != null -> listOf(if (range == null) fragment else fragment.selection(range))
        else -> emptyList()
    }
    val attachments = when (selection.kind) {
        Kind.Attachment -> files.filter { it.id == selection.attachmentId || it.storageId == selection.attachmentId }
        Kind.Message -> if (range == null) {
            files + reply?.steps.orEmpty().flatMap { step -> step.events.flatMap { it.files } }
        } else emptyList()
        Kind.FinalResult, Kind.ToolCall, Kind.ToolResult -> files.takeIf { range == null }.orEmpty()
        else -> emptyList()
    }.distinctBy { it.storageId ?: it.id }.map { file ->
        file.snapshot(user?.sentAttachments?.firstOrNull { it.metadata.fileId == file.id })
    }
    if (selection.kind == Kind.Attachment && attachments.isEmpty()) return null
    if (fragments.all { it.text.isBlank() } && attachments.isEmpty()) return null
    return ChatSourceSnapshot(
        source = ChatSourceReference(
            messageId, revisionId, ChatSourceReference.Kind.valueOf(selection.kind.name),
            blockId = selection.stepId,
            toolCallId = selection.eventId,
            resourceVersionId = attachments.singleOrNull()?.versionId.takeIf { selection.kind == Kind.Attachment },
            range = range,
        ),
        role = if (user == null) ChatSourceSnapshot.Role.Agent else ChatSourceSnapshot.Role.User,
        agentName = agentName.takeIf { user == null },
        fragments = fragments,
        attachments = attachments,
    )
}

internal fun ChatSourceSnapshot.target(displayId: String) = AuroraReferenceSelection(
    messageId = displayId,
    kind = Kind.valueOf(source.kind.name),
    stepId = source.blockId,
    eventId = source.toolCallId,
    attachmentId = source.resourceVersionId,
    range = source.range?.let { AuroraReferenceSelection.Range(it.start, it.endExclusive) },
    revisionId = source.revisionId,
)

internal fun List<AuroraReferenceSelection>.mergeReferenceSelections(): List<AuroraReferenceSelection> {
    val merged = mutableListOf<AuroraReferenceSelection>()
    for (next in this) {
        var incoming = next
        var insertAt = merged.size
        val key = next.copy(range = null, selectedText = null)
        for (index in merged.indices.reversed()) {
            val previous = merged[index]
            if (previous.copy(range = null, selectedText = null) != key) continue
            val start = incoming.range
            val end = previous.range
            if (start == null && end == null && previous.selectedText != incoming.selectedText) continue
            if (start != null && end != null) {
                val range = ChatSourceReference.Range(start.start, start.endExclusive)
                val prior = ChatSourceReference.Range(end.start, end.endExclusive)
                if (!range.overlapsOrAdjoins(prior)) continue
                val combined = range.covering(prior)
                incoming = incoming.copy(
                    range = AuroraReferenceSelection.Range(combined.start, combined.endExclusive),
                    selectedText = null,
                )
            } else if (start != end) continue
            insertAt = index
            merged.removeAt(index)
        }
        merged.add(insertAt, incoming)
    }
    return merged
}

private fun Fragment.selection(range: ChatSourceReference.Range): Fragment = copy(
    text = text.substring(range.start, range.endExclusive),
    formattedMarkdown = if (format == Format.Markdown) text.formatQuoteMarkdown(range) else null,
)

private fun ChatMessageBody.referenceFragments(range: ChatSourceReference.Range?): List<Fragment> = buildList {
    var offset = 0
    for (node in content) {
        val fragments = when (node) {
            is Node.Text -> listOf(Fragment(node.value, Format.Markdown))
            is Node.Quote -> node.snapshot.fragments
        }
        for (fragment in fragments) {
            val end = offset + fragment.text.length
            if (range == null || offset >= range.start && end <= range.endExclusive) {
                if (fragment.text.isNotEmpty()) add(fragment)
            } else {
                val startInFragment = maxOf(range.start, offset) - offset
                val endInFragment = minOf(range.endExclusive, end) - offset
                if (startInFragment < endInFragment) {
                    add(fragment.selection(ChatSourceReference.Range(startInFragment, endInFragment)))
                }
            }
            offset = end
        }
    }
}

private fun ChatHistoryEntry.Reply.completeReferenceFragments(): List<Fragment> = buildList {
    fun block(value: String?, format: Format) {
        if (value.isNullOrBlank()) return
        if (isNotEmpty()) add(Fragment("\n\n", Format.Plain))
        add(Fragment(value, format))
    }
    block(interim?.takeUnless { text -> steps.any { it.result == text } }, Format.Markdown)
    for (step in steps) {
        block(step.details, Format.Plain)
        for (event in step.events) {
            block(event.command, Format.Code)
            block(event.details.ifBlank { event.summary }, event.detailsFormat?.let { Format.valueOf(it.name) } ?: Format.Plain)
        }
        block(step.result, Format.Markdown)
    }
    block(text, Format.Markdown)
}

private fun String.resolveRange(selection: AuroraReferenceSelection): ChatSourceReference.Range? {
    val range = selection.range?.takeIf { it.start >= 0 && it.endExclusive in (it.start + 1)..length }
    val selected = selection.selectedText?.takeIf(String::isNotEmpty)
    if (range != null && (selected == null || substring(range.start, range.endExclusive).contains(selected))) {
        return ChatSourceReference.Range(range.start, range.endExclusive)
    }
    if (selected == null) return null
    val hint = range?.start ?: 0
    val near = listOf(lastIndexOf(selected, hint), indexOf(selected, hint)).filter { it >= 0 }
        .minByOrNull { kotlin.math.abs(it - hint) }
    return if (near != null) ChatSourceReference.Range(near, near + selected.length)
    else range?.let { ChatSourceReference.Range(it.start, it.endExclusive) }
}

private fun ChatAttachment.snapshot(sent: SentMessageAttachment?): ChatSourceSnapshot.Attachment {
    val version = storageId ?: id
    val metadata = sent?.metadata ?: SentAttachmentMetadata(id, name, source?.description, mimeType, size)
    val uri = sent?.uri ?: source?.location?.originalUri ?: "urn:heartbeat:attachment:${version}"
        .takeIf { transferMode == AttachmentTransferMode.Resource && storageId != null }
    val content = when {
        sent is SentMessageAttachment.Resource -> sent.content
        content.isEmpty() && (storageId == null || transferMode == AttachmentTransferMode.ResourceLink && size != 0L) -> null
        base64Encoded -> SentMessageAttachment.Resource.Content.Binary(content)
        else -> SentMessageAttachment.Resource.Content.Text(content)
    }
    return ChatSourceSnapshot.Attachment(version, metadata, transferMode, uri, content, image?.thumbnail)
}

internal fun AiStudioScreenState.Message.asReferenceEntry(): ChatHistoryEntry {
    val message = when (this) {
        is AiStudioScreenState.User -> ChatHistoryEntry.User(
            id, time, text, promptId, files.map { it.forTurn() }, ChatHistoryEntry.Delivery.valueOf(delivery.name),
            error, answers, ChatHistoryEntry.Scenario.valueOf(scenario.name), ChatHistoryEntry.Access.valueOf(access.name),
            messageBody = references?.body,
        )
        is AiStudioScreenState.Reply -> ChatHistoryEntry.Reply(
            id, time, text, promptId, interim, ChatHistoryEntry.ReplyStatus.valueOf(status.name),
            steps.map { step ->
                ChatHistoryEntry.Step(
                    step.id, step.title, step.details, ChatHistoryEntry.StepStatus.valueOf(step.status.name),
                    step.result, step.activeTitle, step.events.map { event ->
                        ChatHistoryEntry.Event(
                            event.id, event.title, event.summary, event.details,
                            ChatHistoryEntry.EventKind.valueOf(event.kind.name), ChatHistoryEntry.StepStatus.valueOf(event.status.name),
                            event.detailLabel, ChatHistoryEntry.DetailFormat.valueOf(event.detailsFormat.name), null,
                            command = event.command, files = event.files.map { it.forTurn() },
                        )
                    },
                )
            }, result?.forTurn(), artifacts = artifacts.map { it.forTurn() },
        )
    }
    return ChatHistoryEntry(0, id.hashCode().toLong(), id.hashCode().toLong(), false, message)
}
