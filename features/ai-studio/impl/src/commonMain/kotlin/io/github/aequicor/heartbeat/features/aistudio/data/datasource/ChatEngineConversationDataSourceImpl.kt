package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTurn
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolOutput
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

internal class ChatEngineConversationDataSourceImpl(private val session: Session) : ChatEngineConversationDataSource {

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
            check(state.status == SessionStatus.Idle || state.status is SessionStatus.Failed)
            val offset = state.messages.items.size
            session.dispatch(SessionEvent.SendPrompt(text, turnId, files.map { "${it.name}\n${it.content}" }))
            check(session.state.value.turn?.id == turnId) { "The engine did not accept this turn" }
            runningId = turnId
            offset
        }
        if (start == null) {
            update(ChatEngineTurn(status = ChatEngineTurn.Status.Stopped))
            return
        }
        try {
            session.state.first { state ->
                val turn = state.turn?.takeIf { it.id == turnId }
                val status = turn?.status?.toDomain() ?: ChatEngineTurn.Status.Failed
                val permission = (state.status as? SessionStatus.AwaitingPermission)?.requests?.firstOrNull()?.let {
                    ChatTurn.Permission(it.requestId, it.description, it.options.map { option ->
                        ChatTurn.Option(option.id, option.label, option.kind)
                    })
                }
                val items = state.messages.items.drop(start)
                update(ChatEngineTurn(
                    text = items.filterIsInstance<SessionTimelineItem.AgentMessage>().filterNot { it.reasoning }
                        .joinToString("") { message -> message.content.joinToString("") { it.display() } },
                    steps = items.mapNotNull { item ->
                        when (item) {
                            is SessionTimelineItem.ToolCall -> item.toDomain()
                            is SessionTimelineItem.AgentMessage -> item.takeIf { it.reasoning }?.toReasoning(
                                status.takeIf { item === items.lastOrNull() } ?: ChatEngineTurn.Status.Completed,
                            )
                            else -> null
                        }
                    },
                    permission = permission,
                    status = status,
                ))
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

    override fun answerPermission(requestId: String, optionId: String) =
        session.dispatch(SessionEvent.AnswerPermission(requestId, optionId))
}

private fun SessionTurn.Status.toDomain(): ChatEngineTurn.Status = when (this) {
    SessionTurn.Status.Running -> ChatEngineTurn.Status.Running
    SessionTurn.Status.Completed -> ChatEngineTurn.Status.Completed
    SessionTurn.Status.Stopped -> ChatEngineTurn.Status.Stopped
    SessionTurn.Status.Failed -> ChatEngineTurn.Status.Failed
}

private fun SessionContent.display(): String = when (this) {
    is SessionContent.Text -> text
    is SessionContent.Resource -> title ?: uri
}

private fun SessionTimelineItem.AgentMessage.toReasoning(status: ChatEngineTurn.Status): ChatHistoryEntry.Step {
    val state = when (status) {
        ChatEngineTurn.Status.Running -> ChatHistoryEntry.StepStatus.Current
        ChatEngineTurn.Status.Completed -> ChatHistoryEntry.StepStatus.Done
        ChatEngineTurn.Status.Stopped -> ChatHistoryEntry.StepStatus.Stopped
        ChatEngineTurn.Status.Failed -> ChatHistoryEntry.StepStatus.Failed
    }
    val title = "Размышления"
    val event = ChatHistoryEntry.Event(
        id, title, "", content.joinToString("") { it.display() }, ChatHistoryEntry.EventKind.Thinking, state,
        null, ChatHistoryEntry.DetailFormat.Markdown, null,
    )
    return ChatHistoryEntry.Step(id, title, "", state, null, title, listOf(event))
}

private fun SessionTimelineItem.ToolCall.toDomain(): ChatHistoryEntry.Step {
    val state = when (status) {
        ToolCallStatus.PENDING, ToolCallStatus.IN_PROGRESS -> ChatHistoryEntry.StepStatus.Current
        ToolCallStatus.COMPLETED -> ChatHistoryEntry.StepStatus.Done
        ToolCallStatus.FAILED -> ChatHistoryEntry.StepStatus.Failed
        ToolCallStatus.CANCELLED -> ChatHistoryEntry.StepStatus.Stopped
    }
    val details = output.joinToString("\n") {
        when (it) {
            is ToolOutput.Content -> it.value.display()
            is ToolOutput.Diff -> it.path + "\n" + it.newText
            is ToolOutput.Terminal -> it.id
        }
    }
    val event = ChatHistoryEntry.Event(
        id, title, title, details, ChatHistoryEntry.EventKind.Check, state, null, ChatHistoryEntry.DetailFormat.Plain, null,
    )
    return ChatHistoryEntry.Step(id, title, "", state, null, title, listOf(event))
}
