package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aiengine.api.session.PendingPermission
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTurn
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallKind
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolOutput
import io.github.aequicor.heartbeat.features.aistudio.data.dao.reasoningPreview
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPng
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

internal class ChatEngineConversationDataSourceImpl(
    private val session: Session,
    private val attachments: ChatAttachmentDataSource,
    private val closeSession: suspend () -> Unit,
) : ChatEngineConversationDataSource {

    private val mutex = Mutex()
    private var cancelled = false
    private var runningId: String? = null

    override val settings get() = session.state.value.settings.toDomain()

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun select(id: String, value: String): ChatEngineOptions = session.selectSetting(id, value).toDomain()

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun send(text: String, files: List<ChatAttachment>, update: suspend (ChatEngineTurn) -> Unit) {
        val turnId = Uuid.random().toString()
        val start = mutex.withLock {
            if (cancelled) return@withLock null
            val state = session.state.value
            if (state.status != SessionStatus.Idle && state.status !is SessionStatus.Failed) {
                throw ChatTurnException("Сессия движка недоступна", notDispatched = true)
            }
            val offset = state.messages.items.size
            val promptFiles = files.map { file ->
                val image = file.image
                if (image != null) SessionEvent.PromptFile.Image(image.mimeType, file.content)
                else SessionEvent.PromptFile.Text("${file.name}\n${file.content}")
            }
            session.dispatch(SessionEvent.SendPrompt(text, turnId, promptFiles = promptFiles))
            check(session.state.value.turn?.id == turnId) { "The engine did not accept this turn" }
            runningId = turnId
            offset
        }
        if (start == null) {
            update(ChatEngineTurn(status = ChatEngineTurn.Status.CancelledBeforeDispatch))
            return
        }
        try {
            var accepted = false
            val decoded = mutableMapOf<SessionContent.Artifact, ChatAttachment?>()
            val replyFiles = linkedMapOf<String, ChatAttachment>()
            val toolFiles = mutableMapOf<String, MutableMap<String, ChatAttachment>>()
            suspend fun receive(artifact: SessionContent.Artifact): ChatAttachment? {
                if (artifact !in decoded) decoded[artifact] = attachments.receive(artifact)
                return decoded[artifact]
            }
            session.state.first { state ->
                val turn = state.turn?.takeIf { it.id == turnId }
                accepted = accepted || turn?.accepted == true
                val missingTurnStatus = if (accepted) ChatEngineTurn.Status.Failed else ChatEngineTurn.Status.SendFailed
                val status = turn?.status?.toDomain() ?: missingTurnStatus
                val permission = (state.status as? SessionStatus.AwaitingPermission)?.requests?.firstOrNull()
                    ?.toChatPermission()
                val items = state.messages.items.drop(start)
                for (item in items) {
                    when (item) {
                        is SessionTimelineItem.AgentMessage -> if (!item.reasoning) {
                            for (artifact in item.content.filterIsInstance<SessionContent.Artifact>()) {
                                val file = receive(artifact) ?: continue
                                replyFiles.getOrPut(file.id) { file }
                            }
                        }
                        is SessionTimelineItem.ToolCall -> for (output in item.output) {
                            val artifact = (output as? ToolOutput.Content)?.value as? SessionContent.Artifact ?: continue
                            val file = receive(item.withReadSource(artifact)) ?: continue
                            toolFiles.getOrPut(item.id) { linkedMapOf() }.getOrPut(file.id) { file }
                        }
                        is SessionTimelineItem.UserMessage, is SessionTimelineItem.ContextCompaction -> Unit
                    }
                }
                val progress = items.toChatTurn(status, permission, toolFiles.mapValues { it.value.values.toList() }).copy(
                    accepted = accepted, artifacts = replyFiles.values.toList(),
                )
                val updateWithFailure = when (turn?.failure) {
                    SessionTurn.Failure.RateLimited -> progress.copy(failure = ChatHistoryEntry.ReplyFailure.RateLimited)
                    SessionTurn.Failure.ImageUnsupported -> progress.copy(rejection = ChatEngineTurn.Rejection.ImageUnsupported)
                    null -> progress
                }
                update(updateWithFailure)
                status != ChatEngineTurn.Status.Running
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (session.state.value.turn?.let { it.id == turnId && it.status == SessionTurn.Status.Running } == true) {
                        session.dispatch(SessionEvent.Cancel)
                    }
                    runningId = null
                }
            }
        }
    }

    @Throwable(CancellationException::class)
    override suspend fun cancel() = mutex.withLock {
        cancelled = true
        if (runningId != null) session.dispatch(SessionEvent.Cancel)
    }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun close() = closeSession()

    override fun answerPermission(requestId: String, optionId: String) =
        session.dispatch(SessionEvent.AnswerPermission(requestId, optionId))
}

private fun PendingPermission.toChatPermission(): ChatTurn.Permission {
    val paths = tool?.paths.orEmpty().ifEmpty { listOfNotNull(tool?.inputPath) }
    return ChatTurn.Permission(
        requestId,
        description,
        options.map { ChatTurn.Option(it.id, it.label, it.kind) },
        paths,
        tool?.command,
        tool?.workingDirectory,
        tool?.requestedAccess,
    )
}

private fun SessionTurn.Status.toDomain(): ChatEngineTurn.Status = when (this) {
    SessionTurn.Status.Running -> ChatEngineTurn.Status.Running
    SessionTurn.Status.Completed -> ChatEngineTurn.Status.Completed
    SessionTurn.Status.Stopped -> ChatEngineTurn.Status.Stopped
    SessionTurn.Status.Rejected -> ChatEngineTurn.Status.Rejected
    SessionTurn.Status.SendFailed -> ChatEngineTurn.Status.SendFailed
    SessionTurn.Status.Failed -> ChatEngineTurn.Status.Failed
}

private fun SessionContent.display(): String = when (this) {
    is SessionContent.Text -> text
    is SessionContent.Resource -> title ?: uri
    is SessionContent.Artifact -> ""
}

internal fun ToolOutput.display(): String = when (this) {
    is ToolOutput.Content -> when (val content = value) {
        is SessionContent.Artifact -> when (val payload = content.payload) {
            is SessionContent.Artifact.Payload.Text -> payload.text
            is SessionContent.Artifact.Payload.Base64 -> content.fileName()
        }
        else -> content.display()
    }
    is ToolOutput.Diff -> path + "\n" + newText
    is ToolOutput.Terminal -> id
    is ToolOutput.Command -> listOfNotNull(
        "stdout " + stdout.trimEnd('\r', '\n').takeUnless { it == "(no output)" }.orEmpty().ifEmpty { "Пусто" },
        "stderr " + stderr.trimEnd('\r', '\n').ifEmpty { "Пусто" },
        exitCode?.let { "Код возврата $it" },
    ).joinToString("\n")
}

private const val MaxArtifactBytes = 1_048_576

private suspend fun ChatAttachmentDataSource.receive(artifact: SessionContent.Artifact): ChatAttachment? {
    val payload = artifact.payload
    val encoded = payload is SessionContent.Artifact.Payload.Base64
    val declaredType = artifact.mimeType?.substringBefore(';')?.trim()?.lowercase()?.takeUnless { it.isBlank() }
        ?: if (encoded) "application/octet-stream" else "text/plain"
    val content: String
    val bytes: ByteArray
    when (payload) {
        is SessionContent.Artifact.Payload.Text -> {
            content = payload.text
            if (content.length > MaxArtifactBytes) return null
            bytes = content.encodeToByteArray()
        }
        is SessionContent.Artifact.Payload.Base64 -> {
            content = payload.data
            if (content.length > (MaxArtifactBytes + 2) / 3 * 4) return null
            bytes = try {
                Base64.decode(content)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
    }
    if (bytes.size > MaxArtifactBytes) return null
    val format = ChatImage.identify(bytes).takeIf { encoded }
    val mimeType = format?.mimeType ?: declaredType
    val name = artifact.fileName(format)
    val image = if (format != null) {
        try {
            if (format == ChatImage.Png) ChatPng.validate(bytes)
            val thumbnail = imageThumbnail(bytes, format, ChatImage.ThumbnailSize)
            thumbnail?.let { ChatAttachment.Image(mimeType, Base64.encode(it)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    } else null
    val kind = when (name.substringAfterLast('.', "").lowercase()) {
        "md", "markdown" -> "Markdown"
        "diff", "patch" -> "Патч"
        "" -> "Файл"
        else -> name.substringAfterLast('.').uppercase()
    }
    // ACP content blocks have no attachment ID. The source and original bytes identify
    // a replay even when the agent changes between text and Base64 resource blocks.
    val source = artifact.fileReference() ?: name
    val id = "artifact-" + Buffer().writeUtf8(source).writeByte(0).write(bytes).readByteString().sha256().hex()
    return ChatAttachment(
        id, name, kind, content, null, null, bytes.size.toLong(),
        image = image, mimeType = mimeType, base64Encoded = encoded,
    )
}

private fun SessionContent.Artifact.fileName(format: ChatImage? = null): String {
    val name = fileReference()?.substringAfterLast('/')?.substringAfterLast('\\')
        ?.filterNot { it.isISOControl() || it in "<>:\"|?*" }?.take(180)?.trim()?.trimEnd('.')
    if (!name.isNullOrBlank() && name != "." && name != "..") return name
    val type = mimeType?.substringBefore(';')?.lowercase()
    val extension = (format ?: ChatImage.forMimeType(type))?.extension ?: when (type) {
        "text/plain" -> "txt"
        "text/markdown" -> "md"
        "application/pdf" -> "pdf"
        else -> "bin"
    }
    return "Вложение.$extension"
}

private fun SessionContent.Artifact.fileReference(): String? =
    uri?.takeUnless { it.startsWith("data:", ignoreCase = true) }?.substringBefore('?')?.substringBefore('#')

private fun SessionTimelineItem.ToolCall.withReadSource(artifact: SessionContent.Artifact): SessionContent.Artifact {
    val path = inputPath ?: paths.singleOrNull()
    return if (path != null && kind == ToolCallKind.READ && artifact.uri == null &&
        artifact.mimeType?.startsWith("image/", ignoreCase = true) == true
    ) artifact.copy(uri = path) else artifact
}

internal fun List<SessionTimelineItem>.toChatTurn(
    status: ChatEngineTurn.Status,
    permission: ChatTurn.Permission? = null,
    toolFiles: Map<String, List<ChatAttachment>> = emptyMap(),
): ChatEngineTurn {
    val steps = mutableListOf<ChatHistoryEntry.Step>()
    val events = mutableListOf<ChatHistoryEntry.Event>()
    val summaries = mutableListOf<String>()
    val preparation = mutableListOf<String>()
    val answer = StringBuilder()
    val firstTool = indexOfFirst { it is SessionTimelineItem.ToolCall }
    val lastWork = indexOfLast { it is SessionTimelineItem.ToolCall || it is SessionTimelineItem.AgentMessage && it.reasoning }
    val lastMessageIndex = indexOfLast { it is SessionTimelineItem.AgentMessage || it is SessionTimelineItem.ToolCall }
    val currentStatus = status.toStepStatus()

    fun finishToolStage(stageStatus: ChatHistoryEntry.StepStatus) {
        if (events.isEmpty()) return
        val firstTool = events.firstOrNull { it.kind != ChatHistoryEntry.EventKind.Thinking }
        val title = if (firstTool == null) "Размышления" else firstTool.operation.stageTitle()
        steps.add(ChatHistoryEntry.Step(
            events.first().id, title, "", stageStatus,
            summaries.takeIf { it.isNotEmpty() }?.joinToString("\n\n"), title, events.toList(),
        ))
        events.clear()
        summaries.clear()
    }

    forEachIndexed { index, item ->
        when (item) {
            is SessionTimelineItem.ToolCall -> {
                if (summaries.isNotEmpty()) finishToolStage(ChatHistoryEntry.StepStatus.Done)
                events.add(item.toToolEvent(toolFiles[item.id].orEmpty()))
            }
            is SessionTimelineItem.AgentMessage -> {
                val text = item.content.joinToString("") { it.display() }
                if (item.reasoning) {
                    if (summaries.isNotEmpty()) finishToolStage(ChatHistoryEntry.StepStatus.Done)
                    val state = if (index == lastMessageIndex) currentStatus else ChatHistoryEntry.StepStatus.Done
                    events.add(item.toReasoningEvent(text, state))
                } else if (index < lastWork) {
                    // ACP marks both intermediate and final output as agent_message_chunk.
                    if (text.isNotBlank()) {
                        if (events.isEmpty() || firstTool == -1 || index < firstTool) preparation.add(text)
                        else summaries.add(text)
                    }
                } else {
                    answer.append(text)
                }
            }
            is SessionTimelineItem.UserMessage, is SessionTimelineItem.ContextCompaction -> Unit
        }
    }
    finishToolStage(currentStatus)
    return ChatEngineTurn(
        answer.toString(), steps, permission, status,
        preparation.takeIf { it.isNotEmpty() }?.joinToString("\n\n"),
    )
}

private fun ChatEngineTurn.Status.toStepStatus(): ChatHistoryEntry.StepStatus = when (this) {
    ChatEngineTurn.Status.Running -> ChatHistoryEntry.StepStatus.Current
    ChatEngineTurn.Status.Completed -> ChatHistoryEntry.StepStatus.Done
    ChatEngineTurn.Status.Stopped, ChatEngineTurn.Status.CancelledBeforeDispatch -> ChatHistoryEntry.StepStatus.Stopped
    ChatEngineTurn.Status.Rejected, ChatEngineTurn.Status.SendFailed, ChatEngineTurn.Status.Failed -> ChatHistoryEntry.StepStatus.Failed
}

private fun SessionTimelineItem.AgentMessage.toReasoningEvent(text: String, state: ChatHistoryEntry.StepStatus) =
    ChatHistoryEntry.Event(
        id, "Размышления", text.reasoningPreview(), text, ChatHistoryEntry.EventKind.Thinking, state,
        null, ChatHistoryEntry.DetailFormat.Markdown, null, ChatHistoryEntry.ToolOperation.THINK,
    )

private fun SessionTimelineItem.ToolCall.toToolEvent(files: List<ChatAttachment>): ChatHistoryEntry.Event {
    val state = when (status) {
        ToolCallStatus.PENDING, ToolCallStatus.IN_PROGRESS -> ChatHistoryEntry.StepStatus.Current
        ToolCallStatus.COMPLETED -> ChatHistoryEntry.StepStatus.Done
        ToolCallStatus.FAILED -> ChatHistoryEntry.StepStatus.Failed
        ToolCallStatus.CANCELLED -> ChatHistoryEntry.StepStatus.Stopped
    }
    val operation = kind?.let { ChatHistoryEntry.ToolOperation.valueOf(it.name) }
    val textOutput = if (files.isEmpty()) output else output.filterNot { it is ToolOutput.Content && it.value is SessionContent.Artifact }
    val read = if (operation == ChatHistoryEntry.ToolOperation.READ && state == ChatHistoryEntry.StepStatus.Done) {
        val content = textOutput.singleOrNull() as? ToolOutput.Content
        (content?.value as? SessionContent.Text)?.text?.let(DshReadEnvelope::matchEntire)
    } else null
    val diff = output.toChatToolDiff()
    val details = diff?.body ?: read?.groupValues?.get(3) ?: textOutput.joinToString("\n", transform = ToolOutput::display)
    val eventKind = when (operation) {
        ChatHistoryEntry.ToolOperation.READ, ChatHistoryEntry.ToolOperation.EDIT,
        ChatHistoryEntry.ToolOperation.DELETE, ChatHistoryEntry.ToolOperation.MOVE -> ChatHistoryEntry.EventKind.File
        ChatHistoryEntry.ToolOperation.SEARCH -> ChatHistoryEntry.EventKind.Search
        ChatHistoryEntry.ToolOperation.EXECUTE, ChatHistoryEntry.ToolOperation.THINK,
        ChatHistoryEntry.ToolOperation.FETCH, ChatHistoryEntry.ToolOperation.SWITCH_MODE,
        ChatHistoryEntry.ToolOperation.OTHER, null -> ChatHistoryEntry.EventKind.Check
    }
    val target = diff?.path ?: read?.groupValues?.get(1) ?: paths.firstOrNull() ?: inputPath
    val label = when (eventKind) {
        ChatHistoryEntry.EventKind.File -> target?.substringAfterLast('/')?.substringAfterLast('\\')
        ChatHistoryEntry.EventKind.Search -> searchPattern
        ChatHistoryEntry.EventKind.Thinking, ChatHistoryEntry.EventKind.Check -> null
    } ?: title
    val detailLabel = when {
        operation == ChatHistoryEntry.ToolOperation.EXECUTE -> workingDirectory
        eventKind == ChatHistoryEntry.EventKind.File || eventKind == ChatHistoryEntry.EventKind.Search -> target
        else -> null
    }
    val format = when {
        diff != null -> ChatHistoryEntry.DetailFormat.Diff
        read?.groupValues?.get(2)?.replace(" ", "") == "file" -> ChatHistoryEntry.DetailFormat.Code
        else -> ChatHistoryEntry.DetailFormat.Plain
    }
    return ChatHistoryEntry.Event(
        id, label, diff?.summary ?: label, details, eventKind, state, detailLabel, format, null, operation,
        command.takeIf { operation == ChatHistoryEntry.ToolOperation.EXECUTE },
        files,
    )
}

// DSH wraps line-numbered text and image metadata in the same read envelope.
private val DshReadEnvelope = Regex(
    """<(?:path|p a t h)>([^\r\n]+)</(?:path|p a t h)>\r?\n""" +
        """<(?:type|t y p e)>(file|f i l e|image|i m a g e)</(?:type|t y p e)>\r?\n""" +
        """<(?:content|c o n t e n t)>\r?\n([\s\S]*)\r?\n</(?:content|c o n t e n t)>""",
)

private fun ChatHistoryEntry.ToolOperation?.stageTitle(): String = when (this) {
    ChatHistoryEntry.ToolOperation.READ -> "Просмотр файлов"
    ChatHistoryEntry.ToolOperation.EDIT, ChatHistoryEntry.ToolOperation.DELETE,
    ChatHistoryEntry.ToolOperation.MOVE -> "Работа с файлами"
    ChatHistoryEntry.ToolOperation.SEARCH -> "Поиск по проекту"
    ChatHistoryEntry.ToolOperation.EXECUTE -> "Работа в терминале"
    ChatHistoryEntry.ToolOperation.THINK, ChatHistoryEntry.ToolOperation.FETCH,
    ChatHistoryEntry.ToolOperation.SWITCH_MODE, ChatHistoryEntry.ToolOperation.OTHER, null -> "Работа по запросу"
}
