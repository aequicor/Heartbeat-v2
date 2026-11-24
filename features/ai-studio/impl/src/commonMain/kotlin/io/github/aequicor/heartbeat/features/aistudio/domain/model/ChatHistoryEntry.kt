package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatHistoryEntry(
    val position: Long,
    val revisionId: Long,
    val messageId: Long,
    val predatesEdit: Boolean,
    val message: Message,
) {

    val recoveredMessage: Message
        get() = when (message) {
            is User -> if (message.delivery == Delivery.Sending) {
                message.copy(delivery = Delivery.Failed, error = InterruptedSendError, retryAllowed = false)
            } else message
            is Reply -> if (message.status == ReplyStatus.Working) {
                message.copy(status = ReplyStatus.Stopped, steps = message.steps.map(Step::stop))
            } else message
        }

    sealed interface Message {

        val id: String
        val time: String
        val text: String
        val promptId: String?
    }

    data class User(
        override val id: String,
        override val time: String,
        override val text: String,
        override val promptId: String?,
        val files: List<ChatAttachment>,
        val delivery: Delivery,
        val error: String?,
        val answers: Boolean,
        val scenario: Scenario,
        val access: Access,
        val retryAllowed: Boolean = true,
    ) : Message

    data class Reply(
        override val id: String,
        override val time: String,
        override val text: String,
        override val promptId: String?,
        val interim: String?,
        val status: ReplyStatus,
        val steps: List<Step>,
        val result: ChatAttachment?,
        val retryAllowed: Boolean = true,
        val failure: ReplyFailure? = null,
        val artifacts: List<ChatAttachment> = emptyList(),
    ) : Message

    data class Step(
        val id: String,
        val title: String,
        val details: String,
        val status: StepStatus,
        val result: String?,
        val activeTitle: String,
        val events: List<Event>,
    ) {

        fun stop(): Step {
            if (status != StepStatus.Current && events.none { it.status == StepStatus.Current }) return this
            return copy(
                status = if (status == StepStatus.Current) StepStatus.Stopped else status,
                events = events.map(Event::stop),
            )
        }
    }

    data class Event(
        val id: String,
        val title: String?,
        val summary: String,
        val details: String,
        val kind: EventKind,
        val status: StepStatus,
        val detailLabel: String?,
        val detailsFormat: DetailFormat?,
        val bodyId: Long?,
        val operation: ToolOperation? = null,
        val command: String? = null,
        val files: List<ChatAttachment> = emptyList(),
    ) {

        fun stop(): Event = when {
            status != StepStatus.Current -> this
            kind == EventKind.Thinking -> copy(status = StepStatus.Stopped, title = InterruptedReasoningTitle)
            else -> copy(status = StepStatus.Stopped, summary = InterruptedToolSummary)
        }
    }

    enum class Delivery { Sending, Sent, Failed }

    enum class ReplyStatus { Working, Completed, Stopped, Failed }

    enum class ReplyFailure { RateLimited }

    enum class StepStatus { Current, Done, Stopped, Failed }

    enum class EventKind { File, Search, Thinking, Check }

    enum class ToolOperation { READ, EDIT, DELETE, MOVE, SEARCH, EXECUTE, THINK, FETCH, SWITCH_MODE, OTHER }

    enum class DetailFormat { Plain, Markdown, Code, Diff }

    enum class Scenario { Standard, SendFailure, DecisionFailure, RunFailure, Optional, Stream }

    enum class Access { ReadOnly, Workspace, Full, Auto }

    data class Page(val entries: List<ChatHistoryEntry>, val before: Long?)

    data class Revision(val role: String, val status: String, val hasAttachments: Boolean) {

        fun requireCommitted() = require(status != "draft") { "The revision is still being written" }

        fun requireEditable(text: String) {
            requireCommitted()
            require(role == "user") { "Only a user message can be edited" }
            require(text.isNotBlank() || hasAttachments) { "A message needs text or an attachment" }
        }
    }
}

internal const val InterruptedSendError = "Отправка прервана при закрытии приложения"
internal const val InterruptedReasoningTitle = "Размышления прерваны"
internal const val InterruptedToolSummary = "Остановлено"
