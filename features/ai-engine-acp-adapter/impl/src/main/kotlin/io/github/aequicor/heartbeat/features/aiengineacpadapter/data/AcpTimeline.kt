package io.github.aequicor.heartbeat.features.aiengineacpadapter.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolOutput

@OptIn(UnstableApi::class)
internal class AcpTimeline {
    var items: List<SessionTimelineItem> = listOf()
        private set
    private var messageNumber = 0L
    private var lastMessageId: String? = null
    private var lastRemoteId: String? = null
    private var turnStart = 0

    fun userPrompt(text: String) {
        turnStart = items.size
        items = items + SessionTimelineItem.UserMessage(nextId(), listOf(SessionContent.Text(text)))
        breakMessage()
    }

    fun userChunk(update: SessionUpdate.UserMessageChunk) {
        val last = items.lastOrNull() as? SessionTimelineItem.UserMessage
        val remoteId = update.messageId?.value
        val value = update.content.toSessionContent()
        if (last != null && continues(last.id, remoteId)) {
            items = items.dropLast(1) + last.copy(content = last.content.append(value))
        } else {
            val id = nextId()
            items = items + SessionTimelineItem.UserMessage(id, listOf(value))
            rememberMessage(id, remoteId)
        }
    }

    fun agentChunk(update: SessionUpdate.AgentMessageChunk) {
        val last = items.lastOrNull() as? SessionTimelineItem.AgentMessage
        val remoteId = update.messageId?.value
        val value = update.content.toSessionContent()
        if (last != null && continues(last.id, remoteId)) {
            items = items.dropLast(1) + last.copy(content = last.content.append(value))
        } else {
            val id = nextId()
            items = items + SessionTimelineItem.AgentMessage(id, listOf(value))
            rememberMessage(id, remoteId)
        }
    }

    fun toolCall(update: SessionUpdate.ToolCall) {
        updateTool(update.toolCallId.value, update.title, update.status?.name, update.content)
    }

    fun toolCallUpdate(update: SessionUpdate.ToolCallUpdate): SessionTimelineItem.ToolCall =
        updateTool(update.toolCallId.value, update.title, update.status?.name, update.content)

    fun cancelTools() {
        items = items.mapIndexed { index, item ->
            if (index >= turnStart && item is SessionTimelineItem.ToolCall && item.status in RunningToolStatuses) {
                item.copy(status = ToolCallStatus.CANCELLED)
            } else {
                item
            }
        }
    }

    fun breakMessage() {
        lastMessageId = null
        lastRemoteId = null
    }

    private fun updateTool(
        toolId: String,
        title: String?,
        status: String?,
        output: List<ToolCallContent>?,
    ): SessionTimelineItem.ToolCall {
        val id = "tool-$toolId"
        val index = items.indexOfFirst { it.id == id }
        val old = items.getOrNull(index) as? SessionTimelineItem.ToolCall
        val tool = SessionTimelineItem.ToolCall(
            id,
            title ?: old?.title ?: toolId,
            status?.let(ToolCallStatus::valueOf) ?: old?.status ?: ToolCallStatus.PENDING,
            output?.map(ToolCallContent::toToolOutput) ?: old?.output.orEmpty(),
        )
        if (old == null) {
            breakMessage()
            items = items + tool
        } else {
            items = items.toMutableList().apply { set(index, tool) }
        }
        return tool
    }

    private fun continues(id: String, remoteId: String?): Boolean =
        lastMessageId == id && (remoteId == null || lastRemoteId == null || remoteId == lastRemoteId)

    private fun rememberMessage(id: String, remoteId: String?) {
        lastMessageId = id
        lastRemoteId = remoteId
    }

    private fun nextId(): String = "message-${++messageNumber}"

    private companion object {
        val RunningToolStatuses = setOf(ToolCallStatus.PENDING, ToolCallStatus.IN_PROGRESS)
    }
}

private fun List<SessionContent>.append(content: SessionContent): List<SessionContent> {
    val last = lastOrNull()
    return if (last is SessionContent.Text && content is SessionContent.Text) {
        dropLast(1) + SessionContent.Text(last.text + content.text)
    } else {
        this + content
    }
}

private fun ContentBlock.toSessionContent(): SessionContent = when (this) {
    is ContentBlock.Text -> SessionContent.Text(text)
    is ContentBlock.Image -> SessionContent.Resource(uri ?: "data:$mimeType;base64,$data", mimeType, null)
    is ContentBlock.Audio -> SessionContent.Resource("data:$mimeType;base64,$data", mimeType, null)
    is ContentBlock.ResourceLink -> SessionContent.Resource(uri, mimeType, title ?: name)
    is ContentBlock.Resource -> when (val value = resource) {
        is EmbeddedResourceResource.TextResourceContents -> SessionContent.Text(value.text)
        is EmbeddedResourceResource.BlobResourceContents -> SessionContent.Resource(value.uri, value.mimeType, null)
    }
}

private fun ToolCallContent.toToolOutput(): ToolOutput = when (this) {
    is ToolCallContent.Content -> ToolOutput.Content(content.toSessionContent())
    is ToolCallContent.Diff -> ToolOutput.Diff(path, oldText, newText)
    is ToolCallContent.Terminal -> ToolOutput.Terminal(terminalId)
}
