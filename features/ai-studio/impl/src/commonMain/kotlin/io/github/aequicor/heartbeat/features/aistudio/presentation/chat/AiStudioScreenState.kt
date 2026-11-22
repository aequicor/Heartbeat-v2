package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Prepared state of the Aurora workspace. History is ordered from newest to oldest. */
public data class AiStudioScreenState(
    val loading: Boolean = true,
    val chats: List<Chat> = listOf(),
    val chat: Chat? = null,
    val chatSearch: String = "",
    val chatSearchVisible: Boolean = false,
    val navigation: List<NavigationRow> = listOf(),
    val usesPagedNavigation: Boolean = false,
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
    val deletionError: DeletionError? = null,
    val renameError: RenameError? = null,
    val sessionBootstrap: SessionBootstrapState? = null,
    val olderMessagesBefore: Long? = null,
    val allowsFileImport: Boolean = false,
    val importingAttachmentsFor: Set<String> = emptySet(),
    val attachmentErrorFor: String? = null,
) {

    public enum class DeletionError { Project, Session }

    public enum class RenameError { Project, Session }

    public data class ChatEnginePickerState(
        val engines: List<Choice> = emptyList(),
        val confirmed: Selection? = null,
        val step: Step? = null,
        val engine: Choice? = null,
        val models: List<Choice>? = null,
        val model: Choice? = null,
        val levels: List<Choice>? = null,
        val level: Choice? = null,
        val loading: Boolean = false,
        val failed: Boolean = false,
        val canApply: Boolean = false,
    ) {

        @Serializable
        public data class Choice(val id: String, val name: String)

        @Serializable
        public data class Selection(val engine: Choice, val model: Choice?, val level: Choice?)

        public enum class Step { Engine, Model, Reasoning }
    }

    public data class SessionBootstrapState(
        val project: Project,
        val error: Error? = null,
        val pendingProject: PendingProject? = null,
        val projectTitle: String? = null,
        val projects: ProjectList? = null,
        val operation: Operation? = null,
        val picker: ChatEnginePickerState = ChatEnginePickerState(),
        val id: String = "",
        val attachments: List<File> = emptyList(),
    ) {
        public data class PendingProject(val workingDirectoryPath: String, val name: String)

        public data class ProjectChoice(val id: String, val title: String)

        public sealed interface ProjectList {
            public data object Loading : ProjectList
            public data class Ready(val choices: List<ProjectChoice>) : ProjectList
            public data object Failed : ProjectList
        }

        public enum class Operation { RegisteringProject, CreatingSession }

        public sealed interface Project {
            public data object Ungrouped : Project
            public data class Existing(val id: String) : Project
        }

        public sealed interface Error {
            public data object EmptyPrompt : Error
            public data object InvalidWorkingDirectory : Error
            public data object InvalidName : Error
            public data object ProjectUnavailable : Error
            public data class ProjectNameTaken(val name: String) : Error
            public data object StorageFailure : Error
        }
    }

    public data class Draft(val chatId: String? = null, val text: String = "")
    public data class LiveText(
        val replyId: String? = null, val text: String = "", val document: ReplyDocument? = null,
    ) {
        public fun textFor(id: String): String? = text.takeIf { replyId == id && it.isNotEmpty() }
    }

    public data class ReplyDocument(val blocks: List<ReplyBlock>, val plainText: String? = null) {
        public companion object {
            public fun plain(source: String): ReplyDocument = ReplyDocument(
                if (source.isEmpty()) listOf() else listOf(
                    ReplyBlock.Paragraph(0, source.lines().map { line -> listOf(ReplyInline(line)) }),
                ),
                plainText = source,
            )
        }
    }

    public sealed interface ReplyBlock {
        /** The start offset in the original message, measured in UTF-16 code units. */
        public val id: Int

        public data class Paragraph(override val id: Int, val lines: List<List<ReplyInline>>) : ReplyBlock
        public data class Heading(override val id: Int, val level: Int, val text: List<ReplyInline>) : ReplyBlock
        public data class Quote(override val id: Int, val blocks: List<ReplyBlock>) : ReplyBlock
        public data class ListBlock(
            override val id: Int,
            val ordered: Boolean,
            val start: Int,
            val items: List<List<ReplyBlock>>,
            val checked: List<Boolean?> = listOf(),
        ) : ReplyBlock

        public data class Table(
            override val id: Int,
            val header: List<List<ReplyInline>>,
            val rows: List<List<List<ReplyInline>>>,
            val alignments: List<ReplyAlignment>,
        ) : ReplyBlock

        public data class Code(
            override val id: Int,
            val language: String?,
            val source: String,
            val lines: List<ReplyCodeLine>,
            val closed: Boolean,
        ) : ReplyBlock

        public data class Diagram(
            override val id: Int,
            val kind: ReplyDiagramKind,
            val source: String,
            val closed: Boolean,
        ) : ReplyBlock

        public data class Rule(override val id: Int) : ReplyBlock
    }

    public data class ReplyInline(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val code: Boolean = false,
        val url: String? = null,
    )

    public enum class ReplyAlignment { Left, Center, Right }
    public enum class ReplyDiagramKind { Mermaid, PlantUml }

    public data class ReplyCodeLine(val text: String, val highlights: List<ReplyCodeHighlight>)

    public data class ReplyCodeHighlight(val start: Int, val end: Int, val kind: ReplyCodeKind)

    public enum class ReplyCodeKind { Keyword, String, Comment, Number, DiffAdd, DiffDelete }

    /** Completed replies are parsed on demand as they enter the viewport. */
    public class ReplyHistoryContent internal constructor(
        private val source: String,
        markdown: Boolean = false,
        final: Boolean = true,
        stream: WorkMarkdownStream? = null,
    ) {
        public val plain: Boolean = !markdown && source.isLiteralReply()

        // A reply can be shown in several windows. Keep one immutable result with its owning message.
        private val parsed = lazy { ReplyMarkdownParser(literalFastPath = !markdown).parse(source, final) }
        private val streaming = stream?.snapshot(source)

        internal var lastStreamDocument: ReplyDocument? = null
        internal var finalDocument: ReplyDocument? = null

        public val available: ReplyDocument?
            get() = finalDocument ?: if (parsed.isInitialized()) parsed.value else lastStreamDocument

        public suspend fun document(worker: CoroutineDispatcher = Dispatchers.Default): ReplyDocument =
            withContext(worker) { finalDocument ?: streaming?.document() ?: parsed.value }
    }

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
        val configuration: ChatEnginePickerState.Selection? = null,
        val projectId: String? = null,
    ) {

        public val isUntitled: Boolean get() = title == UntitledTitle
        public val isUngrouped: Boolean
            get() = when (projectId) {
                null -> project == UngroupedProjectKey
                else -> projectId == ProjectId.Ungrouped.value.toString()
            }

        public companion object {
            // These values also appear in existing workspace snapshots and navigation keys.
            internal const val UntitledTitle = "Новый диалог"
            internal const val UngroupedProjectKey = Project.UngroupedTitle
        }
    }

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
        val page: Int? = null,
        val loadError: NavigationLoadError? = null,
    ) {

        public val isUntitled: Boolean get() = kind == NavigationKind.Chat && title == Chat.UntitledTitle
    }

    public enum class NavigationKind { Project, Chat, More, Section, MoreProjects, MoreChildren }

    public data class NavigationLoadError(val diagnostic: String, val firstPage: Boolean, val retrying: Boolean)

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

    // Workspace snapshots and history headers store these discriminator values.
    @Serializable
    @SerialName("io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User")
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
        val retryAllowed: Boolean = true,
    ) : Message

    @Serializable
    public enum class Delivery { Sending, Sent, Failed }

    @Serializable
    @SerialName("io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply")
    public data class Reply(
        override val id: String,
        override val time: String,
        val steps: List<Step> = listOf(),
        val interim: String? = null,
        val text: String = "",
        val status: ReplyStatus = ReplyStatus.Working,
        val result: File? = null,
        val promptId: String? = null,
        val retryAllowed: Boolean = true,
    ) : Message {
        @Transient
        public val interimContent: ReplyHistoryContent? = interim?.let { ReplyHistoryContent(it) }

        @Transient
        public val historyContent: ReplyHistoryContent? =
            if (status == ReplyStatus.Working) null else ReplyHistoryContent(text)
    }

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
    ) {
        @Transient
        public val resultContent: ReplyHistoryContent? = result?.let { ReplyHistoryContent(it) }
    }

    @Serializable
    public data class WorkEvent(
        val id: String,
        val title: String?,
        val summary: String,
        val details: String,
        val kind: WorkEventKind,
        val status: StepStatus,
        val detailLabel: String? = null,
        val detailsFormat: DetailFormat = restoredDetailsFormat(id, kind, detailLabel),
        @Transient internal val storedBodyId: Long? = null,
    ) {
        @Transient
        public var markdownDetails: ReplyHistoryContent? = when {
            detailsFormat != DetailFormat.Markdown || details.isBlank() -> null
            else -> ReplyHistoryContent(details, markdown = true, final = status != StepStatus.Current)
        }
            private set

        internal fun appendDetails(fragment: String, summaryFragment: String, stream: WorkMarkdownStream?): WorkEvent {
            val text = details + fragment
            if (stream == null) return copy(summary = text, details = text)
            return copy(summary = summary + summaryFragment, details = text).also { next ->
                if (next.markdownDetails != null) {
                    next.markdownDetails = ReplyHistoryContent(text, markdown = true, final = false, stream = stream)
                }
            }
        }

        public val preview: String
            get() = if (kind == WorkEventKind.Thinking) summary.ifBlank { emptyThinking } else summary

        public val detailText: String get() = details.ifBlank {
            if (kind == WorkEventKind.Thinking) emptyThinking else "Ожидаю данные…"
        }

        private val emptyThinking: String
            get() = if (status == StepStatus.Current) "Ожидаю текст…" else "Текст не получен."

        public companion object {

            // Workspace snapshots before detailsFormat were saved by this specific file tool.
            private fun restoredDetailsFormat(id: String, kind: WorkEventKind, label: String?): DetailFormat =
                if (id == "read-notes" && kind == WorkEventKind.File && label == "Файл: architecture.md") {
                    DetailFormat.Markdown
                } else {
                    DetailFormat.Plain
                }
        }
    }

    @Serializable
    public enum class WorkEventKind { File, Search, Thinking, Check }

    @Serializable
    public enum class DetailFormat { Plain, Markdown }

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
        @Transient internal val storedAttachmentId: String? = null,
        @Transient internal val storedSizeBytes: Long? = null,
    ) {
        public val openId: String get() = storedAttachmentId ?: id

        public val isMarkdown: Boolean
            get() = kind.equals("Markdown", ignoreCase = true) ||
                name.substringAfterLast('.', "").lowercase() in MarkdownExtensions

        @Transient
        public val markdownContent: ReplyHistoryContent? =
            if (isMarkdown) ReplyHistoryContent(content, markdown = true) else null

        public companion object {

            private val MarkdownExtensions = setOf("md", "markdown")
        }
    }

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
            val allowOnce: Boolean = true,
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
