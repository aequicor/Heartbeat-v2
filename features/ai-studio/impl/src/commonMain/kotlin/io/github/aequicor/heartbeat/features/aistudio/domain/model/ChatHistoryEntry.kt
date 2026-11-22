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
                message.copy(delivery = Delivery.Failed, error = "Отправка прервана при закрытии приложения")
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

        fun stop(): Step = if (status == StepStatus.Current) {
            copy(status = StepStatus.Stopped, events = events.map(Event::stop))
        } else this
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
    ) {

        fun stop(): Event = when {
            status != StepStatus.Current -> this
            kind == EventKind.Thinking -> copy(status = StepStatus.Stopped, title = "Размышления прерваны")
            else -> copy(status = StepStatus.Stopped, summary = "Остановлено")
        }
    }

    enum class Delivery { Sending, Sent, Failed }

    enum class ReplyStatus { Working, Completed, Stopped, Failed }

    enum class StepStatus { Current, Done, Stopped, Failed }

    enum class EventKind { File, Search, Thinking, Check }

    enum class DetailFormat { Plain, Markdown }

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
