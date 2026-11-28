package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.attachmentImagePreviewLimit
import io.github.aequicor.heartbeat.features.aistudio.domain.model.BackgroundReplyIdPrefix
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPng
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ImageMark
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ImageMarkDescription
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.isMarkdownAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.io.encoding.Base64
import kotlin.math.roundToInt

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
    val attachmentLoadFailures: Set<String> = emptySet(),
    val removedAttachmentLinks: Set<String> = emptySet(),
    val promptReferences: PromptReferences? = null,
    val fileChoices: List<File> = listOf(),
    val attachments: List<File> = listOf(),
    val fileSearch: String = "",
    val fileSearchVisible: Boolean = false,
    val viewer: File? = null,
    val viewerCopyError: Boolean = false,
    val editedImageCopyDrafts: Map<String, List<EditedImageCopyDraft>> = emptyMap(),
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
    val chatEnginePicker: ChatEnginePickerState? = null,
    val applyingSecurityModeFor: Set<String> = emptySet(),
    val securityModeErrors: Map<String, String> = emptyMap(),
    val olderMessagesBefore: Long? = null,
    val allowsFileImport: Boolean = false,
    val importingAttachmentsFor: Set<String> = emptySet(),
    val pendingAttachments: Map<String, List<File>> = emptyMap(),
    val attachmentErrorFor: String? = null,
    val attachmentErrorMessage: String? = null,
    val openedHistory: OpenedHistory? = null,
    val artifactToSave: File? = null,
    val fileToCopy: File? = null,
    val submittingDrafts: Set<String> = emptySet(),
    val referenceJump: AuroraReferenceJump? = null,
    val referenceUnavailable: Boolean = false,
    val protocolSubagents: Map<String, SubagentConversation> = emptyMap(),
    val openProtocolSubagentId: String? = null,
) {

    public val subagentConversation: SubagentConversation?
        get() {
            openProtocolSubagentId?.let { return protocolSubagents[it] }
            val jump = referenceJump?.takeIf { it.opensSubagentChat } ?: return null
            val target = jump.target
            val reply = live?.takeIf { it.id == target.messageId }
                ?: messages.filterIsInstance<Reply>().firstOrNull { it.id == target.messageId } ?: return null
            val event = reply.steps.firstOrNull { it.id == target.stepId }
                ?.events?.firstOrNull { it.id == target.eventId } ?: return null
            return SubagentConversation(
                event.subagentTitle(), event.details.ifBlank { event.summary }, event.status,
                jump.subagentMessages, jump.subagentHistoryLoading,
            )
        }

    public val visibleSubagents: List<Subagent>
        get() {
            if (sessionBootstrap != null) return emptyList()
            val protocol = protocolSubagents.map { (id, child) -> Subagent(id, child.title, protocol = true) }
            if (chat?.configuration != null) return subagents + protocol
            return subagents + protocol + (sequenceOf(live) + messages.asSequence().filterIsInstance<Reply>())
                .filterNotNull().distinctBy(Reply::id)
                .flatMap { reply ->
                    reply.steps.asSequence().flatMap { step ->
                        step.events.asSequence().filter { it.isSubagentLaunch() }.map { event ->
                            val target = AuroraReferenceSelection(
                                reply.id, AuroraReferenceSelection.Kind.ToolCall, step.id, event.id,
                            )
                            Subagent("${reply.id}/${event.id}", event.subagentTitle(), target)
                        }
                    }
                }.toList()
        }

    public fun isPromptBlockedBySecurityMode(chat: Chat): Boolean =
        chat.securityMode?.requiresUserChoice == true || chat.id in applyingSecurityModeFor || chat.id in securityModeErrors

    public data class OpenedHistory(val newestMessageId: String?)

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
        val failure: Failure? = null,
        val canApply: Boolean = false,
        val securityMode: SecurityMode? = null,
        val securityModeBusy: Boolean = false,
        val securityModeError: String? = null,
    ) {

        @Serializable
        public data class Choice(val id: String, val name: String)

        @Serializable
        public data class Selection(val engine: Choice, val model: Choice?, val level: Choice?)

        public enum class Step { Engine, Model, Reasoning }

        public enum class Failure { LoadOptions, ApplyConfiguration }
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

    public data class Draft(
        val chatId: String? = null,
        val text: String = "",
        val references: AuroraDraftReferences? = null,
    )

    public data class PromptReferences(
        val draftId: String,
        val projectId: String?,
        val query: String,
        val entries: List<Entry>,
    ) {

        public data class Entry(
            val category: Category,
            val name: String,
            val uri: String,
            val detail: String?,
            val image: ImageAttachment? = null,
            val snapshotId: String? = null,
        )

        public enum class Category { CatalogAttachment, SessionFile, ProjectFile, Skill }
    }

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
        val attachmentReference: Boolean = false,
        val originalRange: IntRange? = null,
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
        @Transient val storageError: String? = null,
        @Transient val contextUsage: ContextUsage? = null,
        @Transient val securityMode: SecurityMode? = null,
    ) {

        internal fun withUsage(usage: ContextUsage?): Chat = copy(contextUsage = usage, contextPercent = usage?.percent)

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

    public data class SecurityMode(val options: List<Option>, val selected: String?, val requiresUserChoice: Boolean) {

        public data class Option(val value: String, val label: String, val description: String?, val group: String?)
    }

    public data class ContextUsage(val used: Long, val size: Long) {

        init {
            require(used >= 0 && size > 0)
        }

        val fraction: Double get() = used.coerceAtMost(size).toDouble() / size
        val percent: Int get() = (100.0 * used.coerceAtMost(size) / size).roundToInt()
        val remaining: Long get() = (size - used).coerceAtLeast(0)
    }

    public data class SubagentConversation(
        val title: String,
        val output: String,
        val status: StepStatus?,
        val messages: List<Message>?,
        val loading: Boolean,
    )

    public data class Subagent(
        val chatId: String,
        val title: String,
        val work: AuroraReferenceSelection? = null,
        val protocol: Boolean = false,
    ) {

        public constructor(chatId: String, title: String) : this(chatId, title, null)

        public val open: AiStudioAction
            get() = when {
                protocol -> AiStudioAction.OpenProtocolSubagent(chatId)
                work != null -> AiStudioAction.OpenSubagentWork(work)
                else -> AiStudioAction.OpenChat(chatId)
            }
    }

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
        Subagents("Запуск субагента"),
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
        @Transient val revisionId: Long? = null,
        @Transient val references: AuroraDraftReferences? = null,
    ) : Message {

        @Transient
        public val markdownContent: ReplyHistoryContent = ReplyHistoryContent(text)
    }

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
        val failure: ReplyFailure? = null,
        val artifacts: List<File> = listOf(),
        @Transient val revisionId: Long? = null,
    ) : Message {
        public val isBackground: Boolean get() = id.startsWith(BackgroundReplyIdPrefix)

        @Transient
        public val interimContent: ReplyHistoryContent? = interim?.let { ReplyHistoryContent(it) }

        @Transient
        public val historyContent: ReplyHistoryContent? =
            if (status == ReplyStatus.Working) null else ReplyHistoryContent(text)
    }

    @Serializable
    public enum class ReplyStatus { Working, Completed, Stopped, Failed }

    @Serializable
    public enum class ReplyFailure { RateLimited }

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
        val operation: ToolOperation? = null,
        val command: String? = null,
        val files: List<File> = listOf(),
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

        public val visibleTitle: String?
            get() = if (kind == WorkEventKind.Thinking && !title.isNullOrBlank()) {
                when (status) {
                    StepStatus.Current -> "Думает..."
                    StepStatus.Done -> "Размышления"
                    StepStatus.Stopped -> "Размышления прерваны"
                    StepStatus.Failed -> title
                }
            } else title

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
    public enum class ToolOperation { READ, EDIT, DELETE, MOVE, SEARCH, EXECUTE, THINK, FETCH, SWITCH_MODE, OTHER }

    @Serializable
    public enum class DetailFormat { Plain, Markdown, Code, Diff }

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
        val image: ImageAttachment? = null,
        val mimeType: String? = null,
        val base64Encoded: Boolean = false,
        val originalUri: String? = null,
        val managedCopyPath: String? = null,
        val sourceSizeBytes: Long? = null,
        val description: String? = null,
        val deferredReading: Boolean = false,
        @Transient internal val catalogUri: String? = null,
        @Transient internal val catalogSnapshotId: String? = null,
    ) {
        public val openId: String get() = storedAttachmentId ?: id

        public val isCatalogSelection: Boolean get() = catalogSnapshotId != null

        public val sizeLabel: String get() = formatAttachmentSize(
            sourceSizeBytes ?: storedSizeBytes ?: if (base64Encoded) {
                val padding = when {
                    content.endsWith("==") -> 2
                    content.endsWith('=') -> 1
                    else -> 0
                }
                (content.length.toLong() * 3 / 4 - padding).coerceAtLeast(0L)
            } else {
                content.encodeToByteArray().size.toLong()
            },
        )

        public val canDeferReading: Boolean
            get() = managedCopyPath != null || originalUri?.startsWith("file:", ignoreCase = true) == true

        public val exceedsInlineImageLimit: Boolean
            get() = image != null && (sourceSizeBytes ?: 0L) > ChatImage.MaxBytes

        public fun hasInvalidImageExtension(displayName: String = name): Boolean {
            val format = ChatImage.forMimeType(image?.mimeType ?: mimeType) ?: return false
            val label = displayName.trim()
            val dot = label.lastIndexOf('.')
            if (dot < 0) return false
            val extension = label.substring(dot + 1).lowercase()
            return extension != format.extension && (format != ChatImage.Jpeg || extension != "jpeg")
        }

        public val isPatch: Boolean get() = kind == "Патч" || mimeType == "text/x-diff" || mimeType == "text/x-patch"
        private val isTextFile: Boolean get() = !base64Encoded || mimeType?.startsWith("text/") == true || isPatch
        public val canPreview: Boolean get() = image != null || isTextFile
        public val isMarkdown: Boolean get() = isTextFile && image == null && isMarkdownAttachment(name, kind)

        public fun previewText(): String? {
            if (!isTextFile) return null
            return if (!base64Encoded) content else try {
                Base64.decode(content).decodeToString(throwOnInvalidSequence = true)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: CharacterCodingException) {
                null
            }
        }

        public fun imageBytes(): ByteArray? {
            val format = ChatImage.forMimeType(image?.mimeType) ?: return null
            val limit = attachmentImagePreviewLimit(kind)
            if (content.length > (limit + 2) / 3 * 4) return null
            return try {
                Base64.decode(content).also { bytes ->
                    require(bytes.size <= limit && ChatImage.identify(bytes) == format)
                    if (format == ChatImage.Png && bytes.size <= ChatImage.MaxBytes) ChatPng.validate(bytes)
                }
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        @Transient
        public val markdownContent: ReplyHistoryContent? =
            if (isMarkdown) previewText()?.let { ReplyHistoryContent(it, markdown = true) } else null
    }

    @Serializable
    public data class ImageAttachment(val mimeType: String, val thumbnail: String? = null) {
        public fun thumbnailBytes(): ByteArray? = thumbnail?.let { ChatPng.decodeOrNull(it, thumbnail = true) }

        public fun acceptsDimensions(width: Int, height: Int): Boolean = ChatImage.acceptsDimensions(width, height)
    }

    @Serializable
    public data class FileDate(val year: Int, val month: Int, val day: Int, val time: String)

    public data class ImageEditor(val text: String, val marks: List<Mark>) {

        public data class Mark(val x: Int, val y: Int, val name: String, val description: String = "")

        public fun crop(left: Int, top: Int, width: Int, height: Int): ImageEditor =
            copy(marks = marks.mapNotNull { it.toDomain().crop(left, top, width, height)?.display() })

        public fun rotateRight(height: Int): ImageEditor =
            copy(marks = marks.map { it.toDomain().rotateRight(height).display() })

        public fun footer(): String = toDomain().footer()

        public fun description(): String? = toDomain().encode()

        private fun toDomain(): ImageMarkDescription = ImageMarkDescription(text, marks.map(Mark::toDomain))

        public companion object {

            public fun fromDescription(description: String?): ImageEditor =
                ImageMarkDescription.decode(description).let { state ->
                    ImageEditor(state.text, state.marks.map(ImageMark::display))
                }
        }
    }

    public data class EditedImageCopyDraft(val file: File, val databaseBusy: Boolean)

    public data class FileGroup(val key: String, val title: String, val files: List<FileRow>)

    public data class FileRow(
        val id: String,
        val name: String,
        val extension: String?,
        val size: String,
        val date: String?,
        val image: ImageAttachment? = null,
        val isMedia: Boolean = image != null,
        val originalUri: String? = null,
        val managedCopyPath: String? = null,
        val canRemove: Boolean = false,
        val usesSavedContent: Boolean = false,
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
            val message: String? = null,
            val canDecline: Boolean = false,
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
        val optionLabels: Map<String, String> = emptyMap(),
        val description: String? = null,
    )

    public data class Answer(
        val options: Set<String> = setOf(),
        val text: String = "",
        val skipped: Boolean = false,
    ) {
        public val answered: Boolean get() = options.isNotEmpty() || text.isNotBlank()
    }
}

private fun AiStudioScreenState.ImageEditor.Mark.toDomain(): ImageMark = ImageMark(x, y, name, description)

private fun ImageMark.display(): AiStudioScreenState.ImageEditor.Mark = AiStudioScreenState.ImageEditor.Mark(x, y, name, description)
