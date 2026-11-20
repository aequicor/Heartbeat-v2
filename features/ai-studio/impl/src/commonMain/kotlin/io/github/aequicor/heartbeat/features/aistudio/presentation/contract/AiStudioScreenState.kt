package io.github.aequicor.heartbeat.features.aistudio.presentation.contract

import kotlinx.serialization.Serializable

/** Prepared state of the Aurora workspace. History is ordered from newest to oldest. */
public data class AiStudioScreenState(
    val loading: Boolean = true,
    val chats: List<Chat> = listOf(),
    val chat: Chat? = null,
    val chatSearch: String = "",
    val chatSearchVisible: Boolean = false,
    val navigation: List<NavigationRow> = listOf(),
    val messages: List<Message> = listOf(),
    val live: Reply? = null,
    val request: Request? = null,
    val activity: Activity = Activity.Idle,
    val restoreAnchor: Anchor = Anchor(),
    val restoreIndex: Int = 0,
    val fileGroups: List<FileGroup> = listOf(),
    val fileChoices: List<File> = listOf(),
    val attachments: List<File> = listOf(),
    val fileSearch: String = "",
    val fileSearchVisible: Boolean = false,
    val viewer: File? = null,
    val chatsVisible: Boolean = false,
    val filesVisible: Boolean = false,
    val expandedWork: Set<String> = setOf(),
    val expandedSteps: Set<String> = setOf(),
    val expandedEvents: Set<String> = setOf(),
    val storageError: String? = null,
    val subagents: List<Subagent> = listOf(),
    val navigationFocusId: String? = null,
) {
    public data class Draft(val chatId: String? = null, val text: String = "")
    public data class LiveText(val replyId: String? = null, val text: String = "")

    @Serializable
    public data class Anchor(val messageId: String? = null, val offset: Int = 0)

    @Serializable
    public data class Chat(
        val id: String,
        val title: String,
        val project: String,
        val scenario: Scenario = Scenario.Standard,
        val access: Access = Access.Workspace,
        val effort: String = "Среднее",
        val contextPercent: Int? = null,
        val parentId: String? = null,
        val navigationStatus: NavigationStatus? = null,
        val quota: Quota? = null,
        val mockSubagent: Boolean = false,
    )

    @Serializable
    public data class Quota(val hours: Int, val usedPercent: Int)

    public data class Subagent(val chatId: String, val title: String)

    public data class NavigationRow(
        val id: String,
        val title: String,
        val kind: NavigationKind,
        val project: String,
        val depth: Int = 0,
        val expanded: Boolean? = null,
        val selected: Boolean = false,
        val status: NavigationStatus? = null,
        val lastChild: Boolean = false,
    )

    public enum class NavigationKind { Project, Chat, More, Section }

    @Serializable
    public enum class NavigationStatus { Working, Attention, Unread }

    @Serializable
    public enum class Scenario(public val label: String) {
        Standard("Уточнение и разрешение"),
        SendFailure("Ошибка отправки"),
        DecisionFailure("Ошибка отправки ответов"),
        RunFailure("Ошибка выполнения"),
        Optional("Уточнение с таймером"),
        Stream("Длинный поток"),
    }

    @Serializable
    public enum class Access(public val label: String) {
        ReadOnly("Только чтение"),
        Workspace("Запись в рабочей области"),
        Full("Полный доступ"),
        Auto("Автоподтверждение"),
    }

    public enum class Activity { Idle, Sending, Running, Waiting, Stopping }

    @Serializable
    public sealed interface Message {
        public val id: String
        public val time: String
    }

    @Serializable
    public data class User(
        override val id: String,
        override val time: String,
        val text: String,
        val files: List<File> = listOf(),
        val delivery: Delivery = Delivery.Sent,
        val error: String? = null,
        val answers: Boolean = false,
        val scenario: Scenario = Scenario.Standard,
        val promptId: String? = null,
        val access: Access = Access.Workspace,
    ) : Message

    @Serializable
    public enum class Delivery { Sending, Sent, Failed }

    @Serializable
    public data class Reply(
        override val id: String,
        override val time: String,
        val steps: List<Step> = listOf(),
        val interim: String? = null,
        val text: String = "",
        val status: ReplyStatus = ReplyStatus.Working,
        val result: File? = null,
        val promptId: String? = null,
    ) : Message

    @Serializable
    public enum class ReplyStatus { Working, Completed, Stopped, Failed }

    @Serializable
    public data class Step(
        val id: String,
        val title: String,
        val details: String,
        val status: StepStatus,
        val events: List<WorkEvent> = listOf(),
        val result: String? = null,
        val activeTitle: String = title,
    )

    @Serializable
    public data class WorkEvent(
        val id: String,
        val title: String?,
        val summary: String,
        val details: String,
        val kind: WorkEventKind,
        val status: StepStatus,
        val detailLabel: String? = null,
    ) {
        public val preview: String
            get() = if (kind == WorkEventKind.Thinking) summary.ifBlank { emptyThinking } else summary

        public val detailText: String get() = details.ifBlank {
            if (kind == WorkEventKind.Thinking) emptyThinking else "Ожидаю данные…"
        }

        private val emptyThinking: String
            get() = if (status == StepStatus.Current) "Ожидаю текст…" else "Текст не получен."
    }

    @Serializable
    public enum class WorkEventKind { File, Search, Thinking, Check }

    @Serializable
    public enum class StepStatus(public val label: String) {
        Current("Выполняется"),
        Done("Готово"),
        Stopped("Остановлено"),
        Failed("Ошибка"),
    }

    @Serializable
    public data class File(
        val id: String,
        val name: String,
        val kind: String,
        val content: String,
        val date: FileDate? = null,
    )

    @Serializable
    public data class FileDate(val year: Int, val month: Int, val day: Int, val time: String)

    public data class FileGroup(val key: String, val title: String, val files: List<FileRow>)

    public data class FileRow(
        val id: String,
        val name: String,
        val extension: String?,
        val size: String,
        val date: String?,
    ) {
        val accessibilityLabel: String = listOfNotNull(name, size, date).joinToString(", ")
    }

    public sealed interface Request {
        public val id: String

        public data class Questions(
            override val id: String,
            val questions: List<Question>,
            val index: Int = 0,
            val answers: Map<String, Answer> = mapOf(),
            val error: String? = null,
            val remaining: Float = 1f,
            val timed: Boolean = false,
            val frozen: Boolean = false,
        ) : Request {
            public val question: Question get() = questions[index]
            public val answer: Answer get() = answers[question.id] ?: Answer()
            public val last: Boolean get() = index == questions.lastIndex
        }

        public data class Permission(
            override val id: String,
            val action: String,
            val target: String,
            val arguments: String,
            val scope: String,
        ) : Request
    }

    public data class Question(
        val id: String,
        val title: String,
        val options: List<String> = listOf(),
        val multiple: Boolean = false,
        val freeText: Boolean = false,
        val required: Boolean = false,
    )

    public data class Answer(
        val options: Set<String> = setOf(),
        val text: String = "",
        val skipped: Boolean = false,
    ) {
        public val answered: Boolean get() = options.isNotEmpty() || text.isNotBlank()
    }
}
