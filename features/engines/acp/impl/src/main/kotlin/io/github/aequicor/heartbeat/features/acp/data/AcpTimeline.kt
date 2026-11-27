package io.github.aequicor.heartbeat.features.acp.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallLocation
import com.agentclientprotocol.model.ToolKind
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallKind
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolOutput
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

@OptIn(UnstableApi::class)
internal class AcpTimeline(private val dshShellOutput: Boolean = false) {
    var items: List<SessionTimelineItem> = listOf()
        private set
    private var messageNumber = 0L
    private var lastMessageId: String? = null
    private var lastRemoteId: String? = null
    private var turnStart = 0
    private val fileChanges = mutableMapOf<String, FileChange>()

    fun userPrompt(text: String) {
        turnStart = items.size
        items = items + SessionTimelineItem.UserMessage(nextId(), listOf(SessionContent.Text(text)))
        breakMessage()
    }

    fun userChunk(update: SessionUpdate.UserMessageChunk) {
        val last = items.lastOrNull() as? SessionTimelineItem.UserMessage
        val remoteId = update.messageId?.value
        val value = update.content.toSessionContent()
        if (last != null && continueMessage(last.id, remoteId)) {
            items = items.dropLast(1) + last.copy(content = last.content.append(value))
        } else {
            val id = nextId()
            items = items + SessionTimelineItem.UserMessage(id, listOf(value))
            rememberMessage(id, remoteId)
        }
    }

    fun agentChunk(content: ContentBlock, remoteId: String?, reasoning: Boolean = false): SessionTimelineItem.AgentMessage {
        val last = items.lastOrNull() as? SessionTimelineItem.AgentMessage
        val value = content.toSessionContent()
        if (last != null && last.reasoning == reasoning && continueMessage(last.id, remoteId)) {
            val merged = last.copy(content = last.content.append(value))
            items = items.dropLast(1) + merged
            return merged
        }
        val id = nextId()
        val message = SessionTimelineItem.AgentMessage(id, listOf(value), reasoning)
        items = items + message
        rememberMessage(id, remoteId)
        return message
    }

    fun directedMessage(messageId: String, content: List<ContentBlock>, fromChild: Boolean, append: Boolean) {
        val id = "session-message-$messageId"
        val index = items.indexOfFirst { it.id == id }
        val current = items.getOrNull(index)
        val values = content.map(ContentBlock::toSessionContent)
        val message = when {
            current is SessionTimelineItem.AgentMessage -> current.copy(
                content = if (append) values.fold(current.content) { parts, value -> parts.append(value) } else values,
            )
            current is SessionTimelineItem.UserMessage -> current.copy(
                content = if (append) values.fold(current.content) { parts, value -> parts.append(value) } else values,
            )
            fromChild -> SessionTimelineItem.AgentMessage(id, values)
            else -> SessionTimelineItem.UserMessage(id, values)
        }
        if (index < 0) items = items + message else items = items.toMutableList().apply { set(index, message) }
        breakMessage()
    }

    fun plan(entries: List<String>) {
        val text = entries.joinToString("\n") { "- $it" }
        val item = SessionTimelineItem.AgentMessage(nextId(), listOf(SessionContent.Text("План\n$text")), reasoning = true)
        items = items + item
        breakMessage()
    }

    fun toolCall(update: SessionUpdate.ToolCall): SessionTimelineItem.ToolCall =
        updateTool(
            update.toolCallId.value, update.title, update.status?.name, update.content, update.kind,
            update.rawInput, update.locations.takeIf { it.isNotEmpty() }, update.rawOutput,
        )

    fun toolCallUpdate(update: SessionUpdate.ToolCallUpdate): SessionTimelineItem.ToolCall =
        updateTool(
            update.toolCallId.value, update.title, update.status?.name, update.content, update.kind,
            update.rawInput, update.locations, update.rawOutput,
        )

    fun cancelTools() {
        items = items.mapIndexed { index, item ->
            if (index >= turnStart && item is SessionTimelineItem.ToolCall && item.status in RunningToolStatuses) {
                fileChanges.remove(item.id)
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
        kind: ToolKind?,
        rawInput: JsonElement?,
        locations: List<ToolCallLocation>?,
        rawOutput: JsonElement?,
    ): SessionTimelineItem.ToolCall {
        val id = "tool-$toolId"
        val index = items.indexOfFirst { it.id == id }
        val old = items.getOrNull(index) as? SessionTimelineItem.ToolCall
        val toolTitle = title ?: old?.title ?: toolId
        val reportedKind = kind?.toEngineKind() ?: old?.kind
        val toolKind = if (reportedKind == null || reportedKind == ToolCallKind.OTHER) {
            toolTitle.knownToolKind() ?: old?.kind ?: reportedKind
        } else reportedKind
        // A supplied rawInput replaces the previous value. Omission and null keep it.
        val hasInput = rawInput != null && rawInput != JsonNull
        val toolStatus = status?.let(ToolCallStatus::valueOf) ?: old?.status ?: ToolCallStatus.PENDING
        val reportedOutput = output?.map(ToolCallContent::toToolOutput) ?: old?.output.orEmpty()
        val savedImage = if (toolTitle == "Image generation" && toolStatus == ToolCallStatus.COMPLETED &&
            reportedOutput.none { it is ToolOutput.Content && it.value !is SessionContent.Text }
        ) rawOutput.savedImage() else null
        if (hasInput) {
            rawInput.fileChange(toolTitle)?.let { fileChanges[id] = it }
        }
        val change = if (toolStatus in RunningToolStatuses) null else fileChanges.remove(id)
        val receipt = if (toolStatus == ToolCallStatus.COMPLETED) reportedOutput.fileReceipt() else null
        val confirmedDiff = receipt?.let { change?.confirmedDiff(it) }
        val previousDiff = old?.output?.singleOrNull() as? ToolOutput.Diff
        val commandOutput = if (dshShellOutput && toolTitle.lowercase() in DshShellTools &&
            toolStatus == ToolCallStatus.COMPLETED
        ) {
            reportedOutput.toDshCommandOutput()
        } else null
        val completedOutput = when {
            commandOutput != null -> listOf(commandOutput)
            confirmedDiff != null -> listOf(confirmedDiff)
            receipt != null && previousDiff != null && old?.status == ToolCallStatus.COMPLETED &&
                receipt.path == previousDiff.path && receipt.scope == previousDiff.scope -> old.output
            savedImage != null -> reportedOutput + ToolOutput.Content(savedImage)
            else -> reportedOutput
        }
        val tool = SessionTimelineItem.ToolCall(
            id,
            toolTitle,
            toolStatus,
            completedOutput,
            toolKind,
            command = if (hasInput) rawInput.stringField("command") else old?.command,
            paths = locations?.map { it.path } ?: old?.paths.orEmpty(),
            inputPath = if (hasInput) {
                rawInput.stringField("file_path") ?: rawInput.stringField("path")
            } else old?.inputPath,
            workingDirectory = if (hasInput) {
                rawInput.stringField("workdir") ?: rawInput.stringField("cwd")
            } else old?.workingDirectory,
            requestedAccess = if (hasInput) rawInput.stringField("sandbox_permissions") else old?.requestedAccess,
            searchPattern = if (hasInput) rawInput.stringField("pattern") else old?.searchPattern,
        )
        if (old == null) {
            breakMessage()
            items = items + tool
        } else {
            items = items.toMutableList().apply { set(index, tool) }
        }
        return tool
    }

    private fun continueMessage(id: String, remoteId: String?): Boolean {
        if (lastMessageId != id) return false
        if (remoteId == null) return true
        val knownId = lastRemoteId
        if (knownId != null && knownId != remoteId) return false
        lastRemoteId = remoteId
        return true
    }

    private fun rememberMessage(id: String, remoteId: String?) {
        lastMessageId = id
        lastRemoteId = remoteId
    }

    private fun nextId(): String = "message-${++messageNumber}"

    private companion object {
        val RunningToolStatuses = setOf(ToolCallStatus.PENDING, ToolCallStatus.IN_PROGRESS)
    }
}

private fun JsonElement?.stringField(name: String): String? =
    textField(name)?.takeIf { it.isNotBlank() }

private fun JsonElement?.textField(name: String): String? =
    ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

// Codex ACP v1 can report a saved image only in rawOutput when inline bytes are unavailable.
private fun JsonElement?.savedImage(): SessionContent.Resource? {
    val path = stringField("savedPath") ?: return null
    if (!runCatching { Path.of(path).isAbsolute }.getOrDefault(false)) return null
    return SessionContent.Resource(path, "image/png", null)
}

private fun JsonElement?.fileChange(title: String): FileChange? {
    if (stringField("file_path") == null) return null
    return when (title.lowercase()) {
        "edit" -> {
            val before = textField("old_string")?.takeIf { it.isNotEmpty() } ?: return null
            val after = textField("new_string")?.takeUnless { it == before } ?: return null
            FileChange(before, after)
        }
        "write" -> textField("content")?.let { FileChange(null, it) }
        else -> null
    }
}

/** DSH ACP forwards the model-facing receipt, without the tool's presentation metadata. */
private class FileChange(private val before: String?, private val after: String) {
    fun confirmedDiff(receipt: FileReceipt): ToolOutput.Diff? {
        if ((before == null) != (receipt.scope == ToolOutput.DiffScope.File)) return null
        return ToolOutput.Diff(receipt.path, before, after, receipt.scope)
    }
}

private class FileReceipt(val path: String, val scope: ToolOutput.DiffScope)

private fun List<ToolOutput>.fileReceipt(): FileReceipt? {
    val text = ((singleOrNull() as? ToolOutput.Content)?.value as? SessionContent.Text)?.text ?: return null
    DshCreatedFile.matchEntire(text)?.let { return FileReceipt(it.groupValues[1], ToolOutput.DiffScope.File) }
    val updated = DshUpdatedFile.matchEntire(text) ?: return null
    val scope = if (text.endsWith("All occurrences were successfully replaced.")) {
        ToolOutput.DiffScope.AllOccurrences
    } else ToolOutput.DiffScope.Replacement
    return FileReceipt(updated.groupValues[1], scope)
}

private val DshCreatedFile = Regex(
    """<(?:path|p a t h)>([^\r\n]+)</(?:path|p a t h)>\r?\n""" +
        """(?:<type>file</type>|<t y p e>f i l e</t y p e>)\r?\n""" +
        """<(?:content|c o n t e n t)>\r?\nCreated file\r?\n</(?:content|c o n t e n t)>""",
)

private val DshUpdatedFile =
    Regex("""The file ([^\r\n]+) has been updated(?: successfully|\. All occurrences were successfully replaced)\.""")

private val DshShellTools = setOf("bash", "pwsh", "powershell")

private fun List<ToolOutput>.toDshCommandOutput(): ToolOutput.Command? {
    val result = ((singleOrNull() as? ToolOutput.Content)?.value as? SessionContent.Text)?.text ?: return null
    // Job acknowledgements have their own lifecycle and do not contain a finished shell transcript.
    if (result.startsWith("started background job ") ||
        result.lineSequence().last().startsWith("[still running after ")
    ) return null
    val exitMarker = DshExitMarker.find(result)
    val exitCode = exitMarker?.groupValues?.get(1)?.toIntOrNull()
    val body = exitMarker?.takeIf { exitCode != null }?.let { result.substring(0, it.range.first) } ?: result
    val delimiter = DshStderrMarker.find(body)
    val stdout = if (delimiter == null) body else body.substring(0, delimiter.range.first)
    val stderr = delimiter?.let { body.substring(it.range.last + 1) }.orEmpty()
    return ToolOutput.Command(stdout, stderr, exitCode)
}

private val DshExitMarker = Regex("""(?:^|\r?\n)\[exit code: (-?\d+)](?:\r?\n)?\z""")
private val DshStderrMarker = Regex("""(?:^|\r?\n)\[stderr]\r?\n""")

// DSH reports these tools by name with kind "other".
private fun String.knownToolKind(): ToolCallKind? = when (lowercase()) {
    "bash", "powershell", "pwsh" -> ToolCallKind.EXECUTE
    "read", "read_image" -> ToolCallKind.READ
    "write", "edit", "apply_patch" -> ToolCallKind.EDIT
    "glob", "grep" -> ToolCallKind.SEARCH
    else -> null
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
    is ContentBlock.Image -> SessionContent.Artifact(uri, mimeType, SessionContent.Artifact.Payload.Base64(data))
    is ContentBlock.Audio -> SessionContent.Artifact(null, mimeType, SessionContent.Artifact.Payload.Base64(data))
    is ContentBlock.ResourceLink -> SessionContent.Resource(uri, mimeType, title ?: name)
    is ContentBlock.Resource -> when (val value = resource) {
        is EmbeddedResourceResource.TextResourceContents ->
            SessionContent.Artifact(value.uri, value.mimeType, SessionContent.Artifact.Payload.Text(value.text))
        is EmbeddedResourceResource.BlobResourceContents ->
            SessionContent.Artifact(value.uri, value.mimeType, SessionContent.Artifact.Payload.Base64(value.blob))
    }
}

private fun ToolCallContent.toToolOutput(): ToolOutput = when (this) {
    is ToolCallContent.Content -> ToolOutput.Content(content.toSessionContent())
    is ToolCallContent.Diff -> ToolOutput.Diff(path, oldText, newText)
    is ToolCallContent.Terminal -> ToolOutput.Terminal(terminalId)
}

private fun ToolKind.toEngineKind(): ToolCallKind = when (this) {
    ToolKind.READ -> ToolCallKind.READ
    ToolKind.EDIT -> ToolCallKind.EDIT
    ToolKind.DELETE -> ToolCallKind.DELETE
    ToolKind.MOVE -> ToolCallKind.MOVE
    ToolKind.SEARCH -> ToolCallKind.SEARCH
    ToolKind.EXECUTE -> ToolCallKind.EXECUTE
    ToolKind.THINK -> ToolCallKind.THINK
    ToolKind.FETCH -> ToolCallKind.FETCH
    ToolKind.SWITCH_MODE -> ToolCallKind.SWITCH_MODE
    ToolKind.OTHER -> ToolCallKind.OTHER
}
