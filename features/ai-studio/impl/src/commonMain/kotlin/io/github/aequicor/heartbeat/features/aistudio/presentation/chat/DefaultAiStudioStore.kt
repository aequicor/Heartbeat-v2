@file:Suppress("TooManyFunctions")

package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.EditedImageKindPrefix
import io.github.aequicor.heartbeat.features.aistudio.editedPngKind
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.AttachmentTransferException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatMessageBody
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceSnapshot
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.EditedImageCopySaveException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.SaveEditedImageCopyUseCase

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatTurnInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ReadSubagentHistoryUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ImportChatAttachmentUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.FindPromptReferencesUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.model.BackgroundChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentCopyTarget
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectsSessionsChange
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions.ProjectsSessionsStoreImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPrompt
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ChatEnginePickerState.Selection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.Throws as Throwable
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Panel as PanelAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Project as ProjectAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Session as SessionAction

/** Workspace commands and mock events are serialized on the component's background scope. */
internal class DefaultAiStudioStore(
    preferences: PreferencesStore,
    private val scope: CoroutineScope,
    private val catalog: ProjectsSessionsInteractor? = null,
    private val persistence: StudioWorkspaceStorage? = null,
    private val catalogChanges: SharedFlow<ProjectsSessionsChange>? = null,
    private val sessionBootstrap: SessionBootstrapInteractor? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val turnInteractor: ChatTurnInteractor? = null,
    private val importAttachment: ImportChatAttachmentUseCase? = null,
    private val findPromptReferences: FindPromptReferencesUseCase? = null,
    private val attachmentReferences: AttachmentReferenceResolver? = null,
    private val imageLoader: DraftAttachmentImageLoader? = null,
    private val saveEditedImageCopy: SaveEditedImageCopyUseCase? = null,
    private val readSubagentHistory: ReadSubagentHistoryUseCase? = null,
) : AiStudioStore {

    override val projectsSessionsStore: ProjectsSessionsStore? = catalog?.let {
        ProjectsSessionsStoreImpl(it, catalogChanges ?: emptyFlow(), scope)
    }

    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private val snapshots = WorkspaceSnapshots(preferences, persistence, useMockWorkspace = catalog == null)
    private val screen = MutableStateFlow(AiStudioScreenState(
        fileChoices = if (importAttachment == null) AuroraMock.inputs else emptyList(),
        allowsFileImport = importAttachment != null,
    ))
    private val enginePicker = sessionBootstrap?.let {
        BootstrapEnginePicker(it, scope, defaultDispatcher, screen, ::enqueue, ::configurationApplied, ::configurationLabels)
    }
    private val remoteTurns = turnInteractor?.let { RemoteChatTurns(it, scope, defaultDispatcher, ::enqueue) }
    private val promptReferenceSearch = findPromptReferences?.let {
        PromptReferenceSearch(it, attachmentReferences, persistence, screen, scope, defaultDispatcher)
    }
    private val editor = MutableStateFlow(Draft())
    private val stream = MutableStateFlow(LiveText())
    private var renderOwner: AuroraTurn? = null
    private var subagentHistoryJob: Job? = null
    private var subagentHistoryChatId: String? = null
    private val renderer = ReplyRenderStream(
        schedule = { milliseconds, action -> scope.launch { delay(milliseconds); enqueue { action() } } },
        publish = { stream.value = it },
    )
    private val fixture = mutableMapOf<String, List<Message>>()
    private val turns = mutableMapOf<String, AuroraTurn>()
    private val pendingSubmissions = mutableMapOf<String, User>()
    private val expandedReplies = mutableMapOf<Pair<String, String>, Pair<Reply, Reply>>()
    private lateinit var workspace: AuroraWorkspace
    private val selected get() = chat(workspace.selectedId)
    private val workspaceFiles = WorkspaceFiles()

    override val screenState = screen.asStateFlow()
    override val draft = editor.asStateFlow()
    override val liveText = stream.asStateFlow()

    private fun reportStorageError() {
        screen.update { it.copy(storageError = SaveError) }
    }

    init {
        if (persistence != null) scope.launch {
            screen.map { it.chat?.takeIf { chat -> chat.configuration != null }?.id }
                .distinctUntilChanged().collectLatest { id ->
                    if (id != null) coroutineScope {
                        launch { observeContextUsage(id, persistence) }
                        persistence.observeSecurityMode(id).retryWhen { failure, _ ->
                            if (failure !is StudioRepositoryException) return@retryWhen false
                            reportStorageError()
                            delay(1_000)
                            true
                        }.collect { securityMode ->
                            enqueue {
                                if (screen.value.chat?.id == id) {
                                    updateChat(id) { it.copy(summary = it.summary.copy(securityMode = securityMode)) }
                                    screen.update { it.copy(chat = it.chat?.copy(securityMode = securityMode)) }
                                }
                            }
                        }
                    }
                }
        }
        if (catalogChanges != null) {
            val previousChange = catalogChanges.replayCache.lastOrNull()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                catalogChanges.filterIsInstance<ProjectsSessionsChange.SessionAdded>().collect { change ->
                    if (change != previousChange) enqueue {
                        addStoredSession(change.session, change.project, payloadLoaded = false)
                        screen.update(::withProjectsSessions)
                    }
                }
            }
        }
        scope.launch {
            workspace = restore()
            val restored = workspace
            launch {
                snapshots.write(
                    restored,
                    onSaved = {
                        screen.update { if (it.storageError == WorkspaceSaveError) it.copy(storageError = null) else it }
                    },
                    onFailure = {
                        screen.update { if (it.storageError == null) it.copy(storageError = WorkspaceSaveError) else it }
                    },
                )
            }
            restoreInitialSession()
            launch {
                projectsSessionsStore?.state?.collect { state ->
                    enqueue {
                        if (state.storageError && screen.value.storageError != RestoreError) reportStorageError()
                        screen.update(::withProjectsSessions)
                    }
                }
            }
            for (command in commands) {
                try {
                    command()
                } catch (_: StudioRepositoryException) {
                    reportStorageError()
                }
            }
        }
        scope.launch {
            while (saveRequests.receiveCatching().isSuccess) {
                delay(SaveInterval)
                enqueue(::save)
            }
        }
    }

    private suspend fun observeContextUsage(id: String, storage: StudioWorkspaceStorage) {
        storage.observeContextUsage(id).retryWhen { failure, _ ->
            if (failure !is StudioRepositoryException) return@retryWhen false
            reportStorageError()
            delay(1_000)
            true
        }.collect { usage ->
            enqueue {
                val chat = screen.value.chat
                if (chat?.id == id && (chat.contextUsage != usage || chat.contextPercent != usage?.percent)) {
                    updateChat(id) { it.copy(summary = it.summary.withUsage(usage)) }
                    screen.update { it.copy(chat = chat.withUsage(usage)) }
                }
            }
        }
    }

    override fun dispatch(action: AiStudioAction) {
        val state = screen.value
        if ((state.subagentConversation != null || state.chat?.mockSubagent == true) && state.sessionBootstrap == null) {
            when (action) {
                is AiStudioAction.EditDraft, AiStudioAction.Send, is AiStudioAction.SendChatDraft,
                is AiStudioAction.MessageReference.EditText, is AiStudioAction.MessageReference.EditContent,
                is AiStudioAction.MessageReference.DeleteQuote -> return
                else -> Unit
            }
        }
        when (action) {
            is AiStudioAction.EditDraft -> {
                // Keep text feedback immediate. Only a small editor snapshot is touched on this path.
                editReferenceDraft(action.chatId) {
                    if (it.references == null) it.copy(text = action.text)
                    else if (!it.references.hasQuotes) {
                        it.withBody(it.body().copy(content = listOf(ChatMessageBody.Node.Text(action.text))))
                    } else it
                }
            }
            is AiStudioAction.MessageReference.EditText -> editReferenceDraft(action.chatId) {
                it.withBody(it.body().editText(action.index, action.value))
            }
            is AiStudioAction.MessageReference.DeleteQuote -> editReferenceDraft(action.chatId) {
                it.withBody(it.body().removeQuote(action.index))
            }
            is AiStudioAction.MessageReference.EditContent -> editReferenceDraft(action.chatId) {
                it.withEditedContent(action.parts)
            }
            AiStudioAction.Send -> screen.value.chat?.id?.let(::submitChatDraft)
            is AiStudioAction.SearchPromptReferences -> promptReferenceSearch?.request(action)
            is AiStudioAction.SendChatDraft -> submitChatDraft(action.chatId)
            AiStudioAction.CancelTurn -> screen.value.chat?.id?.let(::requestTurnCancellation)
            is AiStudioAction.CancelChatTurn -> requestTurnCancellation(action.chatId)
            is AiStudioAction.AnswerPermission -> (action.chatId ?: screen.value.chat?.id)?.let { id ->
                enqueue { answerPermission(id, action) }
            }
            else -> enqueue { handle(action) }
        }
    }

    private fun enqueue(command: suspend () -> Unit) {
        commands.trySend(command)
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun handle(action: AiStudioAction) {
        when (action) {
            is AiStudioAction.EditDraft, AiStudioAction.Send, is AiStudioAction.SendChatDraft,
            is AiStudioAction.SearchPromptReferences,
            AiStudioAction.CancelTurn, is AiStudioAction.CancelChatTurn, is AiStudioAction.AnswerPermission,
            -> Unit
            is AiStudioAction.MessageReference -> handleMessageReference(action)
            is AiStudioAction.ReadAt -> readAt(action)
            is AiStudioAction.LoadOlderMessages -> if (screen.value.chat?.id == action.chatId &&
                selected.olderBefore == action.before
            ) loadOlderMessages()
            is AiStudioAction.OpenChat -> openChat(action.id)
            is AiStudioAction.ArchiveChat -> updateProjectsSessions(SessionAction.Archive(action.id))
            is AiStudioAction.RenameSession -> updateProjectsSessions(SessionAction.Rename(action.id, action.title))
            is AiStudioAction.DeleteSession -> updateProjectsSessions(SessionAction.Delete(action.id))
            AiStudioAction.NewChat -> newChat()
            is AiStudioAction.SearchChats, AiStudioAction.ExpandChatSearch, AiStudioAction.CollapseChatSearch,
            AiStudioAction.ExpandChats, AiStudioAction.CollapseChats,
            -> Unit
            AiStudioAction.CreateSessionWithoutProject -> showSessionBootstrap(SessionBootstrapState.Project.Ungrouped)
            AiStudioAction.CreateSession -> {
                val project = screen.value.chat?.projectId?.let { SessionBootstrapState.Project.Existing(it) }
                showSessionBootstrap(project ?: SessionBootstrapState.Project.Ungrouped, screen.value.chat?.project)
            }
            is AiStudioAction.CreateSessionInProject -> showSessionBootstrapForProject(action.id)
            AiStudioAction.LoadBootstrapProjects -> loadBootstrapProjects(screen, catalog, defaultDispatcher)
            AiStudioAction.DismissSessionBootstrap -> {
                val state = screen.value
                state.sessionBootstrap?.let { draft ->
                    (draft.attachments + state.pendingAttachments[draft.id].orEmpty()).forEach(::discardAttachmentCopy)
                }
                enginePicker?.close()
                screen.update(::withoutSessionBootstrap)
            }
            is AiStudioAction.EnginePicker -> handleEnginePicker(action)
            is AiStudioAction.BootstrapProject -> {
                val project = screen.value.sessionBootstrap?.project
                updateBootstrapProject(screen, sessionBootstrap, defaultDispatcher, action)
                val bootstrap = screen.value.sessionBootstrap
                if (bootstrap != null && bootstrap.project != project) {
                    screen.update { state ->
                        state.copy(
                            sessionBootstrap = state.sessionBootstrap?.copy(
                                attachments = bootstrap.attachments.filter { it.catalogSnapshotId == null },
                            ),
                            promptReferences = null,
                        )
                    }
                    enginePicker?.start(bootstrap) { picker ->
                        screen.update { state ->
                            val current = state.sessionBootstrap
                            if (current?.id == bootstrap.id && current.project == bootstrap.project) {
                                state.copy(sessionBootstrap = current.copy(picker = picker))
                            } else state
                        }
                    }
                }
            }
            is AiStudioAction.SubmitSessionBootstrap -> {
                val draftId = screen.value.sessionBootstrap?.id ?: return
                if (!hasPendingAttachmentWork(draftId)) {
                    submitSessionBootstrap(screen, enginePicker, action, ::openBootstrappedSession)
                }
            }
            is AiStudioAction.RenameProject -> updateProjectsSessions(ProjectAction.Rename(action.id, action.title))
            is AiStudioAction.DeleteProject -> updateProjectsSessions(ProjectAction.Delete(action.id))
            is AiStudioAction.NewProjectChat -> newChat(action.project)
            is AiStudioAction.SetScenario -> updateSummary { it.copy(scenario = action.scenario) }
            is AiStudioAction.SelectSecurityMode -> selectSecurityMode(action)
            is AiStudioAction.SetAccess -> updateSummary { it.copy(access = action.access) }
            is AiStudioAction.SetEffort -> updateSummary { it.copy(effort = action.effort) }
            is AiStudioAction.Attach -> if (importAttachment == null) attach(action.fileId) else Unit
            is AiStudioAction.ImportAttachment -> importAttachments(action.draftId, AiStudioAction.ImportAttachments.Origin.File) { importFiles ->
                importFiles(listOf(action.reference))
            }
            is AiStudioAction.ImportAttachments -> importAttachments(action.draftId, action.origin, action.source)
            is AiStudioAction.AttachmentPickerFailed -> reportAttachmentError(action.draftId)
            is AiStudioAction.ConfirmAttachment -> confirmAttachment(action)
            is AiStudioAction.CancelAttachment ->
                consumePendingAttachment(action.draftId, action.fileId)?.let(::discardAttachmentCopy)
            is AiStudioAction.UpdateDraftAttachment -> updateDraftAttachment(action)
            is AiStudioAction.SaveViewedImageCopy -> saveViewedImageCopy(action)
            is AiStudioAction.RetryEditedImageCopy -> {
                findEditedCopyDraft(action.sessionId, action.id)?.let { file ->
                    val original = loadEditedCopyDraft(file) ?: return
                    persistEditedImageCopy(action.sessionId, original, openOnSuccess = false)
                }
            }
            is AiStudioAction.DownloadEditedImageCopy -> {
                if (screen.value.artifactToSave == null) {
                    val file = findEditedCopyDraft(action.sessionId, action.id)?.let { loadEditedCopyDraft(it) } ?: return
                    screen.update { it.copy(artifactToSave = file) }
                }
            }
            is AiStudioAction.Detach -> detach(action.fileId)
            is AiStudioAction.Retry -> retry(action.id, action.expectedFailedRevisionId)
            is AiStudioAction.EditMessage -> editMessage(action.id)
            is AiStudioAction.InteractWithRequest, is AiStudioAction.Choose,
            is AiStudioAction.EditAnswer, is AiStudioAction.MoveQuestion,
            is AiStudioAction.SkipQuestion, is AiStudioAction.SubmitAnswers, is AiStudioAction.DeclineQuestionnaire -> {
                val id = workspace.selectedId
                val remote = remoteTurns
                if (remote != null && (remote.isRunning(id) || remote.request(id) != null)) {
                    remote.answerQuestionnaire(id, action)
                    screen.update { if (it.chat?.id == id) it.copy(request = remote.request(id)) else it }
                } else turns[id]?.answer(action)
            }
            is AiStudioAction.ToggleFiles -> {
                workspaceFiles.toggleFiles(screen, action.visible, persistence)
                if (action.visible) screen.value.chat?.id?.let { restoreEditedCopyDrafts(it) }
            }
            is AiStudioAction.ShowFileSearch -> screen.update { workspaceFiles.setSearchVisible(it, action.visible) }
            is AiStudioAction.SearchFiles -> screen.update { workspaceFiles.search(it, action.text) }
            is AiStudioAction.AttachCatalogFile -> attachCatalogFile(action)
            is AiStudioAction.OpenFile -> openViewer(workspaceFiles.find(action.id))
            is AiStudioAction.RemoveUnavailableFile -> persistence?.let {
                workspaceFiles.removeUnavailable(screen, it, action.sessionId, action.id)
            }
            is AiStudioAction.OpenResult -> openViewer(findMessage(action.id).let { (it as? Reply)?.result })
            is AiStudioAction.OpenArtifact -> findArtifact(action.replyId, action.fileId)?.let { openViewer(it) }
            is AiStudioAction.CopyFile -> if (screen.value.fileToCopy == null) {
                val opened = loadAttachment(workspaceFiles.find(action.id)) ?: return
                screen.update { it.copy(fileToCopy = opened) }
            }
            is AiStudioAction.FileCopyFinished -> screen.update {
                it.copy(
                    fileToCopy = null,
                    storageError = if (action.failed) "Не удалось скопировать вложение" else it.storageError,
                )
            }
            is AiStudioAction.SaveFile -> saveAttachment(workspaceFiles.find(action.id))
            is AiStudioAction.SaveArtifact -> saveAttachment(findArtifact(action.replyId, action.fileId))
            is AiStudioAction.ArtifactSaveFinished -> screen.update {
                it.copy(
                    artifactToSave = null,
                    storageError = if (action.failed) "Не удалось сохранить вложение" else it.storageError,
                )
            }
            AiStudioAction.CloseViewer -> openViewer(null)
            is AiStudioAction.ToggleWork -> screen.update { it.copy(expandedWork = it.expandedWork.toggle(action.id)) }
            is AiStudioAction.ToggleStep -> screen.update {
                it.copy(expandedSteps = it.expandedSteps.toggle("${action.replyId}/${action.stepId}"))
            }
            is AiStudioAction.ToggleWorkEvent -> if (screen.value.chat != null) {
                screen.toggleStoredEvent(selected, action, persistence, expandedReplies)
            }
            is AiStudioAction.OpenProtocolSubagent -> screen.update {
                if (it.sessionBootstrap != null || action.sessionId !in it.protocolSubagents) it
                else it.copy(openProtocolSubagentId = action.sessionId, referenceJump = null)
            }
            is AiStudioAction.OpenSubagentWork -> openSubagentWork(action.target)
            AiStudioAction.CloseSubagentConversation -> screen.update {
                it.copy(referenceJump = null, openProtocolSubagentId = null)
            }
            is AiStudioAction.SetWorkExpanded -> if (screen.value.chat != null) {
                screen.setWorkExpanded(selected, action, persistence, expandedReplies)
            }
        }
    }

    private fun requestTurnCancellation(id: String) {
        val remote = remoteTurns
        if (remote?.requestCancellation(id) != true) {
            enqueue {
                if (remote?.request(id) != null) remote.cancelBackground(id)
                else turns[id]?.stop()
            }
            return
        }
        screen.update {
            if (it.chat?.id == id && remote.isStopping(id)) it.copy(activity = Activity.Stopping, request = null) else it
        }
    }

    private suspend fun answerPermission(id: String, action: AiStudioAction.AnswerPermission) {
        val remote = remoteTurns
        if (remote != null && (remote.isRunning(id) || remote.request(id) != null)) {
            remote.answer(id, action.requestId, action.allow)
        } else turns[id]?.answer(action)
    }

    private suspend fun restoreInitialSession() {
        val active = workspace.activeChats()
        val last = active.firstOrNull { it.id == workspace.selectedId } ?: active.firstOrNull()
        if (last != null) openChat(last.id, acknowledgeResult = false)
        if (screen.value.chat == null) {
            showSessionBootstrap(SessionBootstrapState.Project.Ungrouped)
        }
    }

    private suspend fun openChat(id: String, acknowledgeResult: Boolean = true) {
        if (enginePicker?.isApplyingToChat == true) return
        val entry = workspace.chats.firstOrNull { it.summary.id == id } ?: return
        val localTurn = id in turns || remoteTurns?.isRunning(id) == true
        val loaded = if (entry.payloadLoaded && localTurn) entry
        else screen.readStored { persistence?.load(entry) ?: entry } ?: return
        enginePicker?.close()
        val target = if (acknowledgeResult) loaded.withReadResult() else loaded
        if (target !== entry) updateChat(id) { target }
        projectsSessionsStore?.dispatch(SessionAction.Select(id))?.getOrThrow()
        projectsSessionsStore?.dispatch(PanelAction.CollapsePanel)?.getOrThrow()
        workspace = workspace.copy(selectedId = id)
        observeBackground(id)
        if (screen.value.chat?.id == id && target.messages === entry.messages) {
            screen.update { withProjectsSessions(withoutSessionBootstrap(it).copy(chat = target.summary, referenceJump = null, openProtocolSubagentId = null)) }
            if (target !== loaded) save()
        } else {
            showChat(target)
        }
        observeSubagentHistory(id)
    }

    private suspend fun loadOlderMessages() {
        val chat = selected
        val loaded = screen.readStored { persistence?.loadOlder(chat) } ?: return
        updateChat(chat.summary.id) { loaded }
        val messages = history(loaded)
        workspaceFiles.refresh(messages)
        screen.update {
            it.copy(
                messages = withPendingSubmission(chat.summary.id, messages), olderMessagesBefore = loaded.olderBefore,
                fileGroups = workspaceFiles.groups(it.fileSearch),
            )
        }
        workspaceFiles.refreshCatalog(screen, persistence)
    }

    private suspend fun showChat(target: AuroraChat) {
        val id = target.summary.id
        val before = editor.getAndUpdate { Draft(id, target.draft, target.messageBody?.displayReferences()) }
        rememberDraft(before)
        val messages = history(target)
        val visibleMessages = withPendingSubmission(id, messages)
        val turn = turns[id]
        requestRender(turn)
        val live = remoteTurns?.reply(id) ?: turn?.reply
        if (turn == null && live != null) renderer.update(id, live.id, live.text)
        val anchorIndex = visibleMessages.indexOfFirst { it.id == target.anchor.messageId }
        val restoredIndex = when {
            live?.id == target.anchor.messageId && live != null -> 0
            anchorIndex < 0 -> 0
            else -> anchorIndex + if (live == null) 0 else 1
        }
        val differentChat = screen.value.chat?.id != id
        if (differentChat) workspaceFiles.selectSession()
        workspaceFiles.refresh(messages)
        screen.update {
            val next = withoutSessionBootstrap(it).copy(
                loading = false,
                chat = target.summary,
                messages = visibleMessages,
                olderMessagesBefore = target.olderBefore,
                live = live,
                referenceJump = null,
                referenceUnavailable = false,
                request = remoteTurns?.request(id) ?: turn?.request,
                activity = remoteTurns?.activity(id) ?: turn?.activity ?: Activity.Idle,
                restoreAnchor = target.anchor,
                restoreIndex = restoredIndex,
                openedHistory = AiStudioScreenState.OpenedHistory(live?.id ?: visibleMessages.firstOrNull()?.id),
                fileGroups = workspaceFiles.groups(it.fileSearch),
                attachmentLoadFailures = if (differentChat) emptySet() else it.attachmentLoadFailures,
                removedAttachmentLinks = if (differentChat) emptySet() else it.removedAttachmentLinks,
                attachments = target.attachments,
                viewer = null,
                subagents = workspace.subagents(id),
                protocolSubagents = if (differentChat) emptyMap() else it.protocolSubagents,
                openProtocolSubagentId = null,
            )
            withProjectsSessions(next)
        }
        workspaceFiles.refreshCatalog(screen, persistence)
        restoreEditedCopyDrafts(id)
        save()
    }


    private suspend fun newChat(project: String? = null) {
        val current = screen.value.chat
        if (catalog != null) {
            val id = project?.let { raw -> workspace.chats.firstOrNull { it.summary.project == raw }?.summary?.projectId ?: raw }
                ?: projectsSessionsStore?.state?.value?.selectedSessionId?.let {
                    catalog.findSession(SessionId.parse(it))?.projectId?.value?.toString()
                } ?: ProjectId.Ungrouped.value.toString()
            showSessionBootstrapForProject(id)
            return
        }
        val chat = Chat(nextId(), Chat.UntitledTitle, project ?: current?.project ?: Chat.UngroupedProjectKey)
        workspace = workspace.insert(AuroraChat(chat))
        openChat(chat.id)
    }


    private fun addStoredSession(session: Session, project: Project, payloadLoaded: Boolean = true): String {
        val id = session.id.value.toString()
        if (workspace.chats.none { it.summary.id == id }) {
            val chat = Chat(
                id, session.title, project.title,
                navigationStatus = session.workState.toNavigationStatus(),
                projectId = project.id.value.toString(),
            )
            workspace = workspace.insert(AuroraChat(chat, payloadLoaded = payloadLoaded))
        }
        return id
    }

    private suspend fun showSessionBootstrap(project: SessionBootstrapState.Project, title: String? = null) {
        if (enginePicker?.isApplyingToChat == true) return
        projectsSessionsStore?.dispatch(PanelAction.CollapsePanel)?.getOrThrow()
        val bootstrap = SessionBootstrapState(project, projectTitle = title, id = nextId())
        val picker = enginePicker
        if (picker == null) publishBootstrap(bootstrap)
        else picker.start(bootstrap) { publishBootstrap(bootstrap.copy(picker = it)) }
    }

    private fun publishBootstrap(bootstrap: SessionBootstrapState) {
        subagentHistoryJob?.cancel()
        screen.update { current ->
            val existing = current.sessionBootstrap
            val next = if (existing?.id == bootstrap.id) current else withoutSessionBootstrap(current)
            val updatedBootstrap = if (existing?.id == bootstrap.id) {
                bootstrap.copy(attachments = existing.attachments)
            } else bootstrap
            withProjectsSessions(next.copy(
                loading = false, sessionBootstrap = updatedBootstrap, filesVisible = false, viewer = null, referenceJump = null,
                openProtocolSubagentId = null,
            ))
        }
    }

    private fun configurationLabels(selection: Selection): Selection {
        val previous = workspace.chats.asSequence().mapNotNull { it.summary.configuration }
            .filter { it.engine.id == selection.engine.id }
        val model = selection.model?.let { choice ->
            if (choice.name != choice.id) choice
            else previous.firstNotNullOfOrNull { it.model?.takeIf { model -> model.id == choice.id && model.name != model.id } }
                ?: if (choice.id.startsWith("[") && choice.id.endsWith("]")) choice.copy(name = "Модель") else choice
        }
        val level = selection.level?.let { choice ->
            if (choice.name != choice.id) choice
            else previous.firstNotNullOfOrNull { it.level?.takeIf { level -> level.id == choice.id && level.name != level.id } } ?: choice
        }
        return selection.copy(model = model, level = level)
    }

    private suspend fun showSessionBootstrapForProject(id: String) {
        val row = screen.value.navigation.firstOrNull { it.project == id && it.expanded != null }
        val project = if (id == ProjectId.Ungrouped.value.toString()) SessionBootstrapState.Project.Ungrouped
        else SessionBootstrapState.Project.Existing(id)
        showSessionBootstrap(project, row?.title)
    }

    private suspend fun openBootstrappedSession(
        action: AiStudioAction.SubmitSessionBootstrap,
        result: SessionBootstrapResult.Created,
        configuration: Selection,
        files: List<File>,
    ) {
        val id = addStoredSession(result.session, result.project)
        val projectId = result.project.id.value.toString()
        val placed = files.map { file -> placeAttachmentInSession(file, projectId, id) }
        val moved = files.zip(placed).filter { (before, after) -> before.managedCopyPath != after.managedCopyPath }
        val prompt = if (moved.isEmpty() || attachmentReferences == null) action.prompt else {
            withContext(defaultDispatcher) { attachmentReferences.rebindLinks(action.prompt, moved) }
        }
        updateChat(id) { chat ->
            chat.editDraft(prompt, placed).copy(
                payloadLoaded = true,
                summary = chat.summary.copy(
                    configuration = configuration,
                    scenario = action.scenario,
                    access = action.access,
                    effort = action.effort ?: chat.summary.effort,
                ),
            )
        }
        if ((moved.isNotEmpty() || placed.any { it.catalogSnapshotId != null }) && snapshots.saveNow(workspace).isSuccess) {
            moved.forEach { (before, _) -> discardAttachmentCopy(before) }
        }
        val remote = remoteTurns
        if (remote == null) {
            openChat(id, acknowledgeResult = false)
        } else {
            screen.update { it.copy(submittingDrafts = it.submittingDrafts + id) }
            try {
                openChat(id, acknowledgeResult = false)
                val draft = editor.value
                if (draft.chatId == id) send(draft)
            } finally {
                if (!remote.isRunning(id)) finishDraftSubmission(id)
            }
        }
        save()
    }

    private suspend fun placeAttachmentInSession(file: File, projectId: String, sessionId: String): File {
        if (file.catalogSnapshotId != null) return file
        val useCase = importAttachment ?: return file
        val location = file.attachmentLocation() as? AttachmentLocation.ManagedCopy ?: return file
        val target = AttachmentCopyTarget(projectId, sessionId, isDraft = false, generatedImage = location.originalUri == null)
        val placed = withContext(defaultDispatcher) { useCase.copyToSession(location, target) }.getOrNull() ?: return file
        return file.copy(managedCopyPath = placed.relativePath)
    }

    private fun readAt(action: AiStudioAction.ReadAt) {
        if (workspace.selectedId != action.chatId) return
        val chat = selected
        val seen = if (action.anchor == Anchor()) chat.withReadResult() else chat
        if (chat.anchor == action.anchor && seen === chat) return
        updateChat(action.chatId) { seen.copy(anchor = action.anchor) }
        if (seen === chat) {
            saveRequests.trySend(Unit)
        } else {
            screen.update { withProjectsSessions(it.copy(chat = seen.summary)) }
            save()
        }
    }

    private suspend fun updateProjectsSessions(action: ProjectsSessionsAction) {
        val previous = workspace
        projectsSessionsStore?.dispatch(action)?.getOrThrow()
        updateWorkspaceAfterNavigation(action)
        screen.update { current ->
            when (action) {
                is ProjectAction.Rename, is SessionAction.Rename -> {
                    val chat = workspace.chats.firstOrNull { it.summary.id == current.chat?.id }
                    withProjectsSessions(current.copy(chat = chat?.summary))
                }
                else -> withProjectsSessions(current)
            }
        }
        if (workspace !== previous) save()
    }

    private fun updateWorkspaceAfterNavigation(action: ProjectsSessionsAction) {
        if (action is SessionAction.Archive) {
            workspace = workspace.copy(archivedChats = workspace.archivedChats + action.id)
            screen.update { it.copy(pendingAttachments = it.pendingAttachments - action.id) }
        }
        val navigation = projectsSessionsStore?.state?.value ?: return
        when (action) {
            is ProjectAction.Delete -> {
                if (navigation.deletionError == null) {
                    removeDeletedChats { it.summary.projectId == action.id }
                }
            }
            is SessionAction.Delete -> {
                if (navigation.deletionError == null) {
                    removeDeletedChats { it.summary.id == action.id }
                }
            }
            is ProjectAction.Rename -> {
                if (navigation.renameError == null) {
                    workspace = workspace.renameProject(action.id, action.title)
                }
            }
            is SessionAction.Rename -> {
                if (navigation.renameError == null) {
                    updateChat(action.id) { it.copy(summary = it.summary.copy(title = action.title)) }
                }
            }
            else -> Unit
        }
    }

    private fun withProjectsSessions(screen: AiStudioScreenState): AiStudioScreenState =
        workspace.withProjectsSessions(screen, projectsSessionsStore?.state?.value ?: ProjectsSessionsState())

    private fun removeDeletedChats(matches: (AuroraChat) -> Boolean) {
        val removed = workspace.deletedChatIds(matches)
        if (removed.isEmpty()) return
        val clearedChat = screen.value.chat?.id in removed
        if (clearedChat) {
            subagentHistoryJob?.cancel()
            editor.value = Draft()
        }
        for (id in removed) {
            remoteTurns?.discard(id)
            turns.remove(id)?.stop()
        }
        workspace = workspace.withoutChats(removed)
        screen.update { it.copy(pendingAttachments = it.pendingAttachments - removed) }
        if (clearedChat) {
            requestRender(null)
            screen.update { current ->
                withProjectsSessions(AiStudioScreenState(
                    loading = false,
                    storageError = current.storageError,
                    fileChoices = current.fileChoices,
                    allowsFileImport = current.allowsFileImport,
                ))
            }
        }
    }

    private val failedSecurityModeSelections = mutableMapOf<String, String?>()

    private fun selectSecurityMode(action: AiStudioAction.SelectSecurityMode) {
        val id = action.chatId
        if (screen.value.sessionBootstrap?.id == id) {
            enginePicker?.chooseSecurityMode(action.value)
            return
        }
        val interactor = turnInteractor ?: return
        if (id != screen.value.chat?.id || id in screen.value.applyingSecurityModeFor || remoteTurns?.isRunning(id) == true) return
        val value = action.value ?: failedSecurityModeSelections[id]
        screen.update { it.copy(applyingSecurityModeFor = it.applyingSecurityModeFor + id, securityModeErrors = it.securityModeErrors - id) }
        scope.launch {
            val result = withContext(defaultDispatcher) {
                val selected = interactor.selectSecurityMode(id, value)
                if (action.value == null && value != null && selected.isSuccess) interactor.selectSecurityMode(id, null) else selected
            }
            enqueue {
                val failure = result.exceptionOrNull()
                if (failure == null) failedSecurityModeSelections.remove(id) else failedSecurityModeSelections[id] = value
                screen.update {
                    val errors = if (failure == null) it.securityModeErrors - id else it.securityModeErrors +
                        (id to (failure.message ?: "Не удалось применить режим"))
                    it.copy(applyingSecurityModeFor = it.applyingSecurityModeFor - id, securityModeErrors = errors)
                }
            }
        }
    }

    private fun updateSummary(transform: (Chat) -> Chat) {
        updateChat(workspace.selectedId) { it.copy(summary = transform(it.summary)) }
        screen.update { withProjectsSessions(it.copy(chat = selected.summary)) }
        save()
    }

    private fun handleEnginePicker(action: AiStudioAction.EnginePicker) {
        val picker = enginePicker ?: return
        val current = screen.value
        if (current.sessionBootstrap != null || current.chatEnginePicker != null) {
            picker.handle(action)
        } else if (action is AiStudioAction.EnginePicker.Open) {
            val chat = current.chat ?: return
            val configuration = chat.configuration ?: return
            if (remoteTurns?.isRunning(chat.id) != true) picker.startExisting(chat.id, configuration, action.step)
        }
    }

    private fun configurationApplied(id: String, configuration: Selection) {
        if (workspace.chats.none { it.summary.id == id }) return
        updateChat(id) { it.copy(summary = it.summary.copy(configuration = configuration)) }
        screen.update { current ->
            if (current.chat?.id == id) current.copy(chat = chat(id).summary) else current
        }
        save()
    }

    private fun attach(id: String) {
        val file = AuroraMock.inputs.firstOrNull { it.id == id } ?: return
        if (selected.attachments.none { it.id == id }) attachments(selected.attachments + file)
    }

    private fun importAttachments(id: String, origin: AiStudioAction.ImportAttachments.Origin, source: AttachmentImportSource) {
        val useCase = importAttachment ?: return
        if (!hasAttachmentDraft(id) || hasPendingAttachmentWork(id)) return
        val copyTarget = if (origin == AiStudioAction.ImportAttachments.Origin.File) null else clipboardTarget(
            id, generatedImage = origin == AiStudioAction.ImportAttachments.Origin.ClipboardImage,
        )
        screen.update {
            it.copy(
                importingAttachmentsFor = it.importingAttachmentsFor + id,
                attachmentErrorFor = it.attachmentErrorFor.takeUnless { failedId -> failedId == id },
                attachmentErrorMessage = it.attachmentErrorMessage.takeUnless { _ -> it.attachmentErrorFor == id },
            )
        }
        scope.launch {
            try {
                source.withReferences { references ->
                    for (reference in references) {
                        val result = withContext(defaultDispatcher) { useCase(reference, copyTarget) }
                        enqueue {
                            result.fold(
                                onSuccess = { enqueueAttachmentConfirmation(id, it.display()) },
                                onFailure = { reportAttachmentError(id) },
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                enqueue { reportAttachmentError(id) }
            } finally {
                enqueue { screen.update { it.copy(importingAttachmentsFor = it.importingAttachmentsFor - id) } }
            }
        }
    }

    private fun clipboardTarget(draftId: String, generatedImage: Boolean): AttachmentCopyTarget {
        val bootstrap = screen.value.sessionBootstrap?.takeIf { it.id == draftId }
        val projectId = if (bootstrap != null) {
            (bootstrap.project as? SessionBootstrapState.Project.Existing)?.id
        } else chat(draftId).summary.projectId
        return AttachmentCopyTarget(
            projectId = projectId ?: ProjectId.Ungrouped.value.toString(),
            scopeId = draftId, isDraft = bootstrap != null, generatedImage = generatedImage,
        )
    }

    private fun hasPendingAttachmentWork(draftId: String): Boolean =
        draftId in screen.value.importingAttachmentsFor || screen.value.pendingAttachments[draftId].orEmpty().isNotEmpty()

    private fun withoutSessionBootstrap(state: AiStudioScreenState): AiStudioScreenState {
        val draftId = state.sessionBootstrap?.id ?: return state
        return state.copy(sessionBootstrap = null, pendingAttachments = state.pendingAttachments - draftId)
    }

    private fun enqueueAttachmentConfirmation(draftId: String, file: File) {
        if (!hasAttachmentDraft(draftId)) {
            discardAttachmentCopy(file)
            return
        }
        screen.update { state ->
            val queue = state.pendingAttachments[draftId].orEmpty() + file
            state.copy(pendingAttachments = state.pendingAttachments + (draftId to queue))
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun confirmAttachment(action: AiStudioAction.ConfirmAttachment) {
        if (!hasAttachmentDraft(action.draftId)) return
        val file = screen.value.pendingAttachments[action.draftId]?.firstOrNull() ?: return
        if (file.id != action.fileId) return
        val details = file.withAttachmentDetails(action.name, action.description, action.deferredReading) ?: return
        val updated = if (action.editedImage != null) {
            importEditedImage(action.draftId, details, action.editedImage, pngCopyName(details.name), action.editedThumbnail) ?: return
        } else details
        addAttachment(action.draftId, updated)
        consumePendingAttachment(action.draftId, action.fileId)
    }

    @Throwable(CancellationException::class)
    private suspend fun updateDraftAttachment(action: AiStudioAction.UpdateDraftAttachment) {
        val state = screen.value
        val bootstrap = state.sessionBootstrap
        val draftId = bootstrap?.id ?: state.chat?.id
        if (draftId != action.draftId || draftId in state.submittingDrafts || bootstrap?.operation != null) return
        if (!hasAttachmentDraft(action.draftId)) return
        val files = bootstrap?.attachments ?: selected.attachments
        val file = files.firstOrNull { it.id == action.fileId } ?: return
        if (file.catalogSnapshotId != null) return
        val details = file.withAttachmentDetails(action.name, action.description, action.deferredReading) ?: return
        val updated = if (action.editedImage != null) {
            importEditedImage(action.draftId, details, action.editedImage, pngCopyName(details.name), action.editedThumbnail) ?: return
        } else details
        val updatedFiles = files.map { if (it.id == file.id) updated else it }
        if (bootstrap == null) {
            attachments(updatedFiles)
        } else {
            screen.update {
                it.copy(sessionBootstrap = it.sessionBootstrap?.copy(attachments = updatedFiles))
                    .clearAttachmentError(action.draftId)
            }
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun importEditedImage(
        scopeId: String,
        file: File,
        source: AttachmentImportSource,
        copyName: String? = null,
        editedThumbnail: String? = null,
        onFailure: () -> Unit = { reportAttachmentError(scopeId, "Не удалось сохранить копию изображения") },
    ): File? {
        val useCase = importAttachment ?: return null
        if (file.image == null) return null
        val target = clipboardTarget(scopeId, generatedImage = true)
        var copy: File? = null
        try {
            source.withReferences { references ->
                val imported = withContext(defaultDispatcher) { useCase(references.single(), target).getOrThrow() }.display()
                val stem = file.name.substringBeforeLast('.').ifBlank { file.name }
                copy = imported.copy(
                    kind = "${EditedImageKindPrefix}Копия",
                    name = copyName ?: "${stem.removeSuffix("-edited")}-edited.png",
                    description = file.description,
                    image = imported.image?.let { it.copy(thumbnail = editedThumbnail ?: it.thumbnail) },
                    deferredReading = file.deferredReading || imported.exceedsInlineImageLimit,
                )
            }
            return checkNotNull(copy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            copy?.let(::discardAttachmentCopy)
            onFailure()
            return null
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun saveViewedImageCopy(action: AiStudioAction.SaveViewedImageCopy) {
        val loader = imageLoader ?: return
        val state = screen.value
        val original = state.viewer?.takeIf { state.chat?.id == action.sessionId && it.openId == action.fileId } ?: return
        val name = action.name?.let(::pngCopyName)
        val source = if (name != null) {
            original.withAttachmentDetails(name, action.description, deferredReading = false) ?: return
        } else original
        val copy = importEditedImage(action.sessionId, source, action.source, name, action.thumbnail) {
            screen.update { it.copy(viewerCopyError = true) }
        } ?: return
        val loaded = withContext(defaultDispatcher) { loader.load(copy).getOrNull() }
        if (loaded == null) {
            discardAttachmentCopy(copy)
            screen.update { it.copy(viewerCopyError = true) }
            return
        }
        if (saveEditedImageCopy == null) {
            screen.update { it.copy(viewer = loaded, viewerCopyError = false) }
        } else {
            persistEditedImageCopy(action.sessionId, loaded, openOnSuccess = true)
        }
    }

    private fun findEditedCopyDraft(sessionId: String, id: String): File? =
        screen.value.editedImageCopyDrafts[sessionId]?.firstOrNull { it.file.id == id }?.file

    @Throwable(CancellationException::class)
    private suspend fun persistEditedImageCopy(sessionId: String, file: File, openOnSuccess: Boolean) {
        val saveCopy = saveEditedImageCopy ?: return
        val location = file.attachmentLocation() ?: return
        val attachment = AttachmentFile(file.id, file.name, location, file.description, file.mimeType, file.sourceSizeBytes)
        val (result, metadata) = withContext(defaultDispatcher) {
            val bytes = checkNotNull(file.imageBytes())
            saveCopy(sessionId, attachment, bytes, file.image?.thumbnail) to bytes.editedPngKind()
        }
        val prepared = file.copy(kind = metadata)
        val snapshot = result.getOrNull()
        val failure = result.exceptionOrNull() as? EditedImageCopySaveException
        screen.update { state ->
            val current = state.editedImageCopyDrafts[sessionId].orEmpty().filterNot { it.file.id == file.id }
            val drafts = if (snapshot == null) {
                current + AiStudioScreenState.EditedImageCopyDraft(prepared, databaseBusy = failure?.busy == true)
            } else current
            val updated = if (drafts.isEmpty()) state.editedImageCopyDrafts - sessionId
            else state.editedImageCopyDrafts + (sessionId to drafts)
            if (state.chat?.id != sessionId) return@update state.copy(editedImageCopyDrafts = updated)
            state.copy(
                editedImageCopyDrafts = updated,
                viewer = if (!openOnSuccess) state.viewer else snapshot?.let { prepared.copy(storedAttachmentId = it) },
                viewerCopyError = false,
                filesVisible = snapshot == null || state.filesVisible,
            )
        }
        if (screen.value.chat?.id == sessionId) workspaceFiles.refreshCatalog(screen, persistence)
    }

    @Throwable(CancellationException::class)
    private suspend fun loadEditedCopyDraft(file: File): File? = withContext(defaultDispatcher) {
        if (file.content.isNotEmpty()) file else imageLoader?.load(file)?.getOrNull()
    }

    @Throwable(CancellationException::class)
    private suspend fun restoreEditedCopyDrafts(sessionId: String) {
        val saveCopy = saveEditedImageCopy ?: return
        val recovered = withContext(defaultDispatcher) { saveCopy.drafts(sessionId).getOrNull() } ?: return
        screen.update { state ->
            val current = state.editedImageCopyDrafts[sessionId].orEmpty()
                .filter { workspaceFiles.find(it.file.id) == null }
            val added = recovered.map { it.display() }.filter { copy ->
                workspaceFiles.find(copy.id) == null && current.none { it.file.id == copy.id }
            }.map { AiStudioScreenState.EditedImageCopyDraft(it, databaseBusy = false) }
            val drafts = current + added
            val updated = if (drafts.isEmpty()) state.editedImageCopyDrafts - sessionId
            else state.editedImageCopyDrafts + (sessionId to drafts)
            state.copy(editedImageCopyDrafts = updated)
        }
    }

    private fun pngCopyName(name: String): String = name.trim().let {
        if (it.endsWith(".png", ignoreCase = true)) it.dropLast(4) + ".png" else "$it.png"
    }

    private fun File.withAttachmentDetails(name: String, description: String?, deferredReading: Boolean): File? {
        val label = name.trim()
        if (label.isEmpty() || label.any(Char::isISOControl) || deferredReading && !canDeferReading) return null
        return copy(name = label, description = description?.trim()?.takeIf(String::isNotEmpty), deferredReading = deferredReading)
    }

    private fun consumePendingAttachment(draftId: String, fileId: String): File? {
        val file = screen.value.pendingAttachments[draftId]?.firstOrNull()?.takeIf { it.id == fileId } ?: return null
        screen.update { state ->
            val queue = state.pendingAttachments[draftId].orEmpty()
            if (queue.firstOrNull()?.id != fileId) return@update state
            val remaining = queue.drop(1)
            val pending = if (remaining.isEmpty()) state.pendingAttachments - draftId
            else state.pendingAttachments + (draftId to remaining)
            state.copy(pendingAttachments = pending)
        }
        return file
    }

    private fun discardAttachmentCopy(file: File) {
        if (file.catalogSnapshotId != null) return
        val useCase = importAttachment ?: return
        val location = file.attachmentLocation() as? AttachmentLocation.ManagedCopy ?: return
        scope.launch {
            withContext(defaultDispatcher) { useCase.discard(location) }
        }
    }

    private suspend fun attachCatalogFile(action: AiStudioAction.AttachCatalogFile) {
        val storage = persistence ?: return
        val state = screen.value
        val bootstrap = state.sessionBootstrap
        val project = bootstrap?.takeIf { it.id == action.draftId }?.project as? SessionBootstrapState.Project.Existing
        val session = state.chat?.takeIf { bootstrap == null && it.id == action.draftId }
        val files = screen.readStored {
            when {
                project != null -> storage.listProjectFiles(project.id)
                session != null -> storage.listFiles(session.id)
                else -> emptyList()
            }
        } ?: return
        val file = files.firstOrNull { it.storedAttachmentId == action.id } ?: run {
            reportAttachmentError(action.draftId, "Вложение недоступно в каталоге")
            return
        }
        val current = screen.value
        if (current.sessionBootstrap?.project != bootstrap?.project || !hasAttachmentDraft(action.draftId)) return
        val draftFiles = current.sessionBootstrap?.attachments ?: current.attachments
        if (draftFiles.any { it.catalogSnapshotId == action.id }) return
        // Different versions of the same original need distinct composer keys.
        addAttachment(action.draftId, file.copy(id = action.id, catalogSnapshotId = action.id))
        save()
    }

    private fun addAttachment(draftId: String, file: File) {
        if (screen.value.sessionBootstrap?.id == draftId) {
            screen.update { state ->
                val bootstrap = state.sessionBootstrap ?: return@update state
                state.copy(sessionBootstrap = bootstrap.copy(attachments = bootstrap.attachments + file))
            }
            return
        }
        if (!hasAttachmentDraft(draftId)) return
        updateChat(draftId) { it.editDraft(files = it.attachments + file) }
        val files = chat(draftId).attachments
        screen.update { if (it.chat?.id == draftId) it.copy(attachments = files) else it }
        save()
    }

    private fun hasAttachmentDraft(id: String): Boolean =
        screen.value.sessionBootstrap?.id == id ||
            workspace.chats.any { it.summary.id == id && it.payloadLoaded && id !in workspace.archivedChats }

    private fun reportAttachmentError(id: String, message: String? = null) {
        if (hasAttachmentDraft(id)) screen.update { it.copy(attachmentErrorFor = id, attachmentErrorMessage = message) }
    }

    private fun AiStudioScreenState.clearAttachmentError(id: String) =
        if (attachmentErrorFor == id) copy(attachmentErrorFor = null, attachmentErrorMessage = null) else this

    private fun detach(fileId: String) {
        val bootstrap = screen.value.sessionBootstrap
        if (bootstrap == null) {
            val chat = screen.value.chat ?: return
            attachments(this.chat(chat.id).attachments.filterNot { it.id == fileId })
        } else {
            screen.update {
                it.copy(sessionBootstrap = it.sessionBootstrap?.let { current ->
                    current.copy(attachments = current.attachments.filterNot { file -> file.id == fileId })
                    }).clearAttachmentError(bootstrap.id)
            }
        }
    }

    private fun attachments(value: List<File>) {
        updateChat(workspace.selectedId) { it.editDraft(files = value) }
        screen.update { it.copy(attachments = value).clearAttachmentError(workspace.selectedId) }
        save()
    }

    private fun editReferenceDraft(chatId: String, edit: (Draft) -> Draft) {
        editor.update { if (it.chatId == chatId) edit(it) else it }
        saveRequests.trySend(Unit)
    }

    private suspend fun handleMessageReference(action: AiStudioAction.MessageReference) {
        val chatId = action.chatId
        if (screen.value.chat?.id != chatId) return
        when (action) {
            is AiStudioAction.MessageReference.EditText, is AiStudioAction.MessageReference.DeleteQuote,
            is AiStudioAction.MessageReference.EditContent -> Unit
            is AiStudioAction.MessageReference.DeleteReply ->
                editReferenceDraft(chatId) { it.withBody(it.body().copy(reply = null)) }
            is AiStudioAction.MessageReference.Reply -> {
                val snapshot = readReferenceSnapshots(chatId, listOf(action.source))?.singleOrNull() ?: return
                editReferenceDraft(chatId) { it.withBody(it.body().copy(reply = snapshot)) }
            }
            is AiStudioAction.MessageReference.Quote -> {
                val quotes = readReferenceSnapshots(chatId, action.sources)?.map { ChatMessageBody.Node.Quote(it) } ?: return
                editReferenceDraft(chatId) { it.withBody(it.body().insertQuotes(action.textIndex, action.offset, quotes)) }
            }
            is AiStudioAction.MessageReference.Jump -> jumpToReference(chatId, action.reference)
            is AiStudioAction.MessageReference.DismissUnavailable -> screen.update { it.copy(referenceUnavailable = false) }
        }
    }

    private suspend fun readReferenceSnapshots(
        chatId: String,
        selections: List<AuroraReferenceSelection>,
    ): List<ChatSourceSnapshot>? {
        val storage = persistence
        val merged = selections.mergeReferenceSelections()
        val selectors = merged.map { it.messageId to it.revisionId }.distinct()
        val messages = mutableMapOf<Pair<String, Long?>, ChatHistoryEntry?>()
        for ((id, revision) in selectors) {
            messages[id to revision] = if (storage == null) findMessage(id)?.asReferenceEntry()
            else screen.readStored { storage.referenceSource(chatId, id, revision) }
        }
        val agent = selected.summary.configuration?.engine?.name
        val snapshots = withContext(defaultDispatcher) {
            merged.mapNotNull { selection -> messages[selection.messageId to selection.revisionId]?.snapshot(selection, agent) }
        }
        if (snapshots.size == merged.size) {
            screen.update { it.copy(referenceUnavailable = false) }
            return snapshots
        }
        screen.update { it.copy(referenceUnavailable = true) }
        return null
    }

    private suspend fun jumpToReference(chatId: String, reference: AuroraMessageReference) {
        val storage = persistence
        val snapshot = reference.snapshot
        val origin = snapshot.source
        val entry = if (storage == null) {
            screen.value.messages.firstOrNull { it.id.hashCode().toLong() == origin.messageId }?.asReferenceEntry()
        } else {
            screen.readStored { storage.referenceSource(chatId, null, origin.revisionId) }
                ?.takeIf { it.messageId == origin.messageId }
        }
        val source = entry?.message?.id
        if (entry == null || source == null) {
            screen.update { it.copy(referenceUnavailable = true) }
            return
        }
        if (storage != null && screen.value.messages.none { it.id == source }) {
            val loaded = screen.readStored { storage.loadThrough(selected, source) } ?: return
            updateChat(chatId) { loaded }
            screen.update { it.copy(messages = withPendingSubmission(chatId, history(loaded)), olderMessagesBefore = loaded.olderBefore) }
        }
        val target = snapshot.target(source)
        if (storage != null) {
            val current = screen.value.messages.firstOrNull { it.id == source }
            val revision = when (current) {
                is User -> current.revisionId
                is Reply -> current.revisionId
                null -> null
            }
            if (revision != entry.revisionId && current != null) {
                val historical = entry.display()
                screen.update { state -> state.copy(messages = state.messages.map { if (it === current) historical else it }) }
            }
        }
        revealWorkTarget(target)
    }

    private suspend fun openSubagentWork(target: AuroraReferenceSelection) {
        val state = screen.value
        if (state.sessionBootstrap != null || state.chat == null) return
        if (state.visibleSubagents.none { it.work == target }) return
        screen.setWorkExpanded(
            selected, AiStudioAction.SetWorkExpanded(
                target.messageId, expanded = true, stepId = target.stepId, eventId = target.eventId,
            ),
            persistence, expandedReplies,
        )
        screen.update {
            it.copy(
                referenceJump = AuroraReferenceJump((it.referenceJump?.sequence ?: 0) + 1, target, opensSubagentChat = true),
                openProtocolSubagentId = null,
            )
        }
    }

    private fun observeSubagentHistory(chatId: String) {
        val reader = readSubagentHistory ?: return
        if (subagentHistoryChatId == chatId && subagentHistoryJob?.isActive == true) return
        subagentHistoryJob?.cancel()
        subagentHistoryChatId = chatId
        val observation = scope.launch(start = CoroutineStart.LAZY) {
            val owner = coroutineContext.job
            val history = withContext(defaultDispatcher) { reader(chatId) }
            history.map { result ->
                result.getOrNull()?.associate { child ->
                    child.sessionId to AiStudioScreenState.SubagentConversation(
                        child.title ?: "Субагент", child.description.orEmpty(), child.status?.let { AiStudioScreenState.StepStatus.valueOf(it.name) },
                        child.messages.map(ChatHistoryEntry.Message::display), loading = false,
                    )
                }
            }.distinctUntilChanged().flowOn(defaultDispatcher).collect { children ->
                if (children != null) enqueue {
                    if (subagentHistoryJob !== owner) return@enqueue
                    screen.update {
                        if (it.chat?.id == chatId && it.sessionBootstrap == null) it.copy(protocolSubagents = children)
                        else it
                    }
                }
            }
        }
        subagentHistoryJob = observation
        observation.start()
    }

    private suspend fun revealWorkTarget(target: AuroraReferenceSelection) {
        val source = target.messageId
        val storage = persistence
        if (target.stepId != null) {
            screen.update { it.copy(expandedWork = it.expandedWork + source, expandedSteps = it.expandedSteps + "$source/${target.stepId}") }
            if (target.eventId != null) {
                screen.setWorkExpanded(
                    selected, AiStudioAction.SetWorkExpanded(source, expanded = true, stepId = target.stepId, eventId = target.eventId),
                    storage, expandedReplies,
                )
            }
        }
        screen.update {
            it.copy(
                referenceUnavailable = false,
                referenceJump = AuroraReferenceJump((it.referenceJump?.sequence ?: 0) + 1, target),
            )
        }
    }

    private fun submitChatDraft(chatId: String) {
        val draft = editor.value
        if (draft.chatId == chatId) enqueue { send(draft) }
    }

    private suspend fun send(draft: Draft) {
        if (enginePicker?.isApplyingToChat == true) return
        val chat = selected
        val chatId = chat.summary.id
        val text = draft.text.trim()
        val activeTurn = chatId in turns || remoteTurns?.isRunning(chatId) == true || remoteTurns?.request(chatId) != null
        if (hasPendingAttachmentWork(chatId)) return
        if (chat.attachments.any { it.hasInvalidImageExtension() }) return
        if (screen.value.isPromptBlockedBySecurityMode(chat.summary)) return
        if (draft.chatId != chatId || !draft.body().canSend(chat.attachments.isNotEmpty(), activeTurn)) return
        val remote = remoteTurns
        if (remote != null && chat.summary.scenario != AiStudioScreenState.Scenario.Subagents) {
            sendToEngine(remote, draft, text, chat)
            return
        }
        val titleSource = text.ifBlank { draft.references?.nodes.orEmpty()
            .filterIsInstance<AuroraDraftReferences.Part.Quote>().firstOrNull()?.reference?.preview.orEmpty() }
        val summary = summaryForPrompt(chat, titleSource) ?: return
        editor.compareAndSet(draft, Draft(chatId))
        updateChat(chatId) { it.editDraft(text = "", files = emptyList(), body = null).copy(summary = summary) }
        val user = User(
            nextId(), MockTime, text, chat.attachments, Delivery.Sending,
            scenario = chat.summary.scenario, access = chat.summary.access, references = draft.references,
        )
        newTurn(user).sendPrompt()
    }

    private suspend fun sendToEngine(remote: RemoteChatTurns, draft: Draft, text: String, chat: AuroraChat) {
        val id = chat.summary.id
        if (editor.value != draft) return
        val pending = User(
            id = nextId(),
            time = Clock.System.now().toString().substringAfter('T').take(5),
            text = text,
            files = chat.attachments,
            delivery = Delivery.Sending,
            scenario = chat.summary.scenario,
            access = chat.summary.access,
            retryAllowed = false,
            references = draft.references,
        )
        pendingSubmissions[id] = pending
        screen.update {
            it.copy(
                messages = if (it.chat?.id == id) withPendingSubmission(id, it.messages) else it.messages,
                submittingDrafts = it.submittingDrafts + id,
                attachmentErrorFor = it.attachmentErrorFor.takeUnless { failedId -> failedId == id },
                attachmentErrorMessage = it.attachmentErrorMessage.takeUnless { _ -> it.attachmentErrorFor == id },
            )
        }
        try {
            val find = findPromptReferences
            val importer = importAttachment
            val referencedImages = if (find == null || importer == null) emptyList() else {
                withContext(defaultDispatcher) { find.importPromptImages(chat, text, importer, attachmentReferences) }
            }
            if (editor.value != draft) return
            rememberDraft(draft)
            if (referencedImages.isNotEmpty()) {
                updateChat(id) { it.editDraft(files = it.attachments + referencedImages) }
                val withReferences = pending.copy(files = this.chat(id).attachments)
                pendingSubmissions[id] = withReferences
                screen.update { state ->
                    if (state.chat?.id != id) state else state.copy(
                        messages = state.messages.map { if (it.id == pending.id) withReferences else it },
                    )
                }
            }
            if (this.chat(id).draftRevision == null) {
                updateChat(id) { it.copy(draftRevision = nextId()) }
            }
            val submitted = this.chat(id)
            if (snapshots.saveNow(workspace).isFailure) {
                reportChatSaveError(id)
                return
            }
            val submission = RemoteChatTurns.Submission.New(
                pending.id, text, submitted.attachments, checkNotNull(submitted.draftRevision), draft.references?.body,
            ) { consumeSubmittedDraft(draft, submitted) }
            startEngineTurn(remote, id, submission)
        } finally {
            if (!remote.isRunning(id)) {
                removePendingSubmission(id, pending.id)
                finishDraftSubmission(id)
            }
        }
    }

    private fun withPendingSubmission(chatId: String, messages: List<Message>): List<Message> {
        val pending = pendingSubmissions[chatId] ?: return messages
        return if (messages.any { it.id == pending.id }) messages else listOf(pending) + messages
    }

    private fun removePendingSubmission(chatId: String, messageId: String) {
        if (pendingSubmissions[chatId]?.id != messageId) return
        pendingSubmissions.remove(chatId)
        screen.update { state ->
            if (state.chat?.id == chatId) state.copy(messages = state.messages.filterNot { it.id == messageId }) else state
        }
    }

    private fun startEngineTurn(remote: RemoteChatTurns, id: String, submission: RemoteChatTurns.Submission) {
        observeBackground(id)
        if (screen.value.chat?.id == id) observeSubagentHistory(id)
        if (remote.isRunning(id)) return
        remote.start(
            id, submission,
            render = { update ->
                if (workspace.chats.any { it.summary.id == id }) renderRemoteTurn(id, update)
            },
            complete = { outcome ->
                if (submission is RemoteChatTurns.Submission.New) {
                    removePendingSubmission(id, submission.messageId)
                    finishDraftSubmission(id)
                }
                if (workspace.chats.any { it.summary.id == id }) {
                    when (val failure = outcome?.exceptionOrNull()) {
                        is StudioRepositoryException -> reportChatSaveError(id)
                        is AttachmentTransferException -> reportAttachmentError(id, failure.message)
                    }
                    val status = chat(id).summary.navigationStatus
                    if (status == NavigationStatus.Working || status == NavigationStatus.Attention) finish(id)
                }
            },
        )
        changeNavigationStatus(id, NavigationStatus.Working)
        screen.update {
            withProjectsSessions(it.copy(chat = selected.summary, activity = remote.activity(id) ?: Activity.Sending))
        }
    }

    private fun reportChatSaveError(id: String) {
        updateChat(id) { it.copy(summary = it.summary.copy(storageError = SaveError)) }
        if (screen.value.chat?.id == id) screen.update { it.copy(chat = chat(id).summary) }
    }

    private fun consumeSubmittedDraft(draft: Draft, submittedChat: AuroraChat) {
        val id = submittedChat.summary.id
        if (workspace.chats.none { it.summary.id == id }) return
        rememberDraft(editor.value)
        val submittedFiles = submittedChat.attachments.toSet()
        updateChat(id) { chat ->
            val sameBody = chat.draft == draft.text && chat.messageBody == draft.references?.body
            val text = if (sameBody) "" else chat.draft
            val body = chat.messageBody.takeUnless { sameBody }
            val files = chat.attachments - submittedFiles
            val remaining = if (chat.draftRevision == submittedChat.draftRevision) {
                // SQLite has already consumed this revision.
                chat.copy(draft = text, attachments = files, messageBody = body)
            } else {
                chat.editDraft(text, files, body)
            }
            remaining.copy(persistedThroughDomain = true)
        }
        editor.compareAndSet(draft, Draft(id))
        finishDraftSubmission(id)
        save()
    }

    private fun finishDraftSubmission(id: String) {
        screen.update { state ->
            if (id !in state.submittingDrafts) state
            else state.copy(
                submittingDrafts = state.submittingDrafts - id,
                attachments = workspace.chats.firstOrNull { it.summary.id == id && state.chat?.id == id }
                    ?.attachments ?: state.attachments,
            )
        }
    }

    private fun observeBackground(id: String) {
        remoteTurns?.observeBackground(
            id,
            render = { renderBackgroundTurn(id, it) },
            onFailure = { if (it is StudioRepositoryException) reportStorageError() },
        )
    }

    private fun renderBackgroundTurn(id: String, turn: BackgroundChatTurn?) {
        if (workspace.chats.none { it.summary.id == id }) return
        val reply = turn?.reply?.display() as? Reply
        if (reply != null) {
            updateChat(id) { it.copy(persistedThroughDomain = true) }
            putMessage(id, reply)
        }
        val remote = checkNotNull(remoteTurns)
        if (workspace.selectedId != id || screen.value.chat?.id != id) {
            if (!remote.isRunning(id) && id !in turns) {
                val status = when {
                    remote.request(id) != null -> NavigationStatus.Attention
                    reply != null -> NavigationStatus.Unread
                    chat(id).summary.navigationStatus == NavigationStatus.Attention -> null
                    else -> return
                }
                if (changeNavigationStatus(id, status)) {
                    screen.update(::withProjectsSessions)
                    save()
                }
            }
            return
        }
        val messages = history(selected)
        if (reply != null) workspaceFiles.refresh(listOfNotNull(screen.value.live) + messages)
        screen.update {
            it.copy(
                messages = withPendingSubmission(id, messages),
                request = remote.request(id) ?: turns[id]?.request,
                activity = remote.activity(id) ?: turns[id]?.activity ?: Activity.Idle,
                fileGroups = workspaceFiles.groups(it.fileSearch),
            )
        }
    }

    private fun renderRemoteTurn(id: String, turn: ChatTurn) {
        if (!chat(id).persistedThroughDomain) updateChat(id) { it.copy(persistedThroughDomain = true) }
        val visible = workspace.selectedId == id && screen.value.chat?.id == id
        if (pendingSubmissions[id]?.id == turn.user.id) pendingSubmissions.remove(id)
        putMessage(id, turn.user.display() as User)
        if (!turn.running) {
            finish(id, turn.reply?.display() as? Reply)
            return
        }
        val waiting = (turn.permission != null || turn.questionnaire != null) && remoteTurns?.isStopping(id) != true
        val status = if (waiting) NavigationStatus.Attention else NavigationStatus.Working
        val navigationChanged = changeNavigationStatus(id, status)
        if (navigationChanged) save()
        if (!visible) {
            if (navigationChanged) screen.update(::withProjectsSessions)
            return
        }
        val reply = remoteTurns?.reply(id)
        if (reply != null) renderer.update(id, reply.id, reply.text)
        val messages = history(selected)
        workspaceFiles.refresh(listOfNotNull(reply) + messages)
        screen.update {
            val next = it.copy(
                chat = selected.summary, messages = messages, live = reply, request = remoteTurns?.request(id),
                activity = remoteTurns?.activity(id) ?: Activity.Running, attachments = selected.attachments,
                fileGroups = workspaceFiles.groups(it.fileSearch),
                expandedSteps = it.stepsExpandedAfterInterim(reply),
            )
            if (navigationChanged) withProjectsSessions(next) else next
        }
    }

    private suspend fun summaryForPrompt(chat: AuroraChat, text: String): Chat? {
        if (!chat.summary.isUntitled) return chat.summary
        val title = ChatPrompt.title(text, chat.attachments.firstOrNull()?.name)
        val store = projectsSessionsStore
        store?.dispatch(SessionAction.Rename(chat.summary.id, title))?.getOrThrow()
        return if (store?.state?.value?.renameError == null) chat.summary.copy(title = title)
        else {
            reportStorageError()
            null
        }
    }

    private fun newTurn(prompt: User, previous: Reply? = null): AuroraTurn {
        val id = workspace.selectedId
        if (previous != null) {
            updateChat(id) { chat -> chat.copy(messages = chat.messages.filterNot { it.id == previous.id }) }
        }
        val turn = AuroraTurn(prompt, this, scope, id, previous?.id ?: nextId())
        turns[id] = turn
        return turn
    }

    private fun retry(id: String, expectedFailedRevisionId: Long?) {
        if (screen.value.isPromptBlockedBySecurityMode(selected.summary)) return
        val message = findMessage(id)
        if ((message as? User)?.retryAllowed == false || (message as? Reply)?.retryAllowed == false) return
        remoteTurns?.let { remote ->
            if (message is User && message.delivery == Delivery.Failed && !message.answers) {
                val revisionId = expectedFailedRevisionId ?: return
                if (message.revisionId != revisionId) return
                startEngineTurn(remote, workspace.selectedId, RemoteChatTurns.Submission.Retry(id, revisionId))
            }
            return
        }
        val turn = turns[workspace.selectedId]
        if (turn != null) {
            if (message is User && message.answers && message.delivery == Delivery.Failed) turn.retryAnswer(message)
            return
        }
        when {
            message is User && message.delivery == Delivery.Failed -> retryUser(message)
            message is Reply && message.status == ReplyStatus.Failed -> {
                val prompt = findMessage(message.promptId) as? User ?: return
                newTurn(prompt, message).resumeExecution(message)
            }
        }
    }

    private fun retryUser(message: User) {
        if (!message.answers) {
            newTurn(message).sendPrompt()
            return
        }
        val prompt = findMessage(message.promptId) as? User ?: return
        val previous = selected.messages.filterIsInstance<Reply>().firstOrNull { it.promptId == prompt.id }
        newTurn(prompt, previous).restoreAnswer(previous, message)
    }

    private suspend fun editMessage(id: String) {
        val message = (findMessage(id) as? User)?.takeUnless(User::answers) ?: return
        val files = screen.readStored { message.files.map { it.withContent(persistence) } } ?: return
        editor.update { it.copy(text = message.text) }
        attachments(files)
    }

    private suspend fun openViewer(file: File?) {
        val opened = loadAttachment(file)
        screen.update { it.copy(viewer = opened, viewerCopyError = false) }
    }

    private suspend fun saveAttachment(file: File?) {
        if (screen.value.artifactToSave != null) return
        val opened = loadAttachment(file) ?: return
        screen.update { it.copy(artifactToSave = opened) }
    }

    private suspend fun loadAttachment(file: File?): File? {
        if (file == null || file.openId in screen.value.removedAttachmentLinks) return null
        return try {
            val stored = if (file.deferredReading && file.storedAttachmentId == null && persistence != null) {
                val sessionId = screen.value.chat?.id ?: return null
                persistence.listFiles(sessionId).firstOrNull { it.id == file.id && it.deferredReading } ?: return null
            } else file
            val loaded = stored.withContent(persistence)
            screen.update { it.copy(attachmentLoadFailures = it.attachmentLoadFailures - file.openId) }
            loaded
        } catch (_: StudioRepositoryException) {
            screen.update { it.copy(attachmentLoadFailures = it.attachmentLoadFailures + file.openId) }
            null
        }
    }

    private fun findMessage(id: String?): Message? = screen.value.messages.firstOrNull { it.id == id }

    private fun findArtifact(replyId: String, fileId: String): File? {
        val reply = screen.value.live?.takeIf { it.id == replyId } ?: findMessage(replyId) as? Reply ?: return null
        return reply.artifacts.firstOrNull { it.id == fileId } ?: reply.steps.asSequence()
            .flatMap { it.events }.flatMap { it.files }.firstOrNull { it.id == fileId }
    }

    internal fun nextId(): String {
        if (persistence != null) return kotlin.uuid.Uuid.random().toString()
        workspace = workspace.copy(serial = workspace.serial + 1)
        return "aurora-${workspace.serial}"
    }

    internal fun putMessage(chatId: String, message: Message) {
        updateChat(chatId) { chat ->
            val index = chat.messages.indexOfFirst { it.id == message.id }
            val history = if (index < 0) listOf(message) + chat.messages else chat.messages.toMutableList().apply {
                set(index, message)
            }
            chat.copy(messages = history)
        }
    }

    internal fun changed(chatId: String, structural: Boolean) {
        val turn = turns[chatId] ?: return
        rememberReply(chatId, turn.reply, deferSave = !structural)
        if (structural) changeNavigationStatus(chatId, turn.workState.toNavigationStatus())
        if (chatId != workspace.selectedId && structural) screen.update(::withProjectsSessions)
        if (chatId == workspace.selectedId) {
            requestRender(turn)
            val messages = if (structural) history(selected) else screen.value.messages
            if (structural) workspaceFiles.refresh(messages)
            screen.update {
                val next = it.copy(
                    chat = selected.summary,
                    messages = messages, live = turn.reply, request = turn.request, activity = turn.activity,
                    expandedSteps = it.stepsExpandedAfterInterim(turn.reply),
                    fileGroups = if (structural) workspaceFiles.groups(it.fileSearch) else it.fileGroups,
                    attachments = selected.attachments,
                )
                if (structural) withProjectsSessions(next) else next
            }
        }
        if (structural) save()
    }

    internal fun streamText(chatId: String, reply: Reply) {
        rememberReply(chatId, reply, deferSave = true)
        if (chatId == workspace.selectedId) requestRender(turns[chatId], reply)
    }

    private fun rememberReply(chatId: String, reply: Reply?, deferSave: Boolean) {
        if (chat(chatId).interrupted === reply) return
        updateChat(chatId) { it.copy(interrupted = reply) }
        if (deferSave) saveRequests.trySend(Unit)
    }

    private fun requestRender(turn: AuroraTurn?, reply: Reply? = turn?.reply) {
        if (renderOwner !== turn || reply == null) {
            renderer.reset()
            stream.value = LiveText()
        }
        renderOwner = turn
        if (turn == null || reply == null) {
            return
        }
        renderer.update(turn, reply.id, reply.text)
    }

    internal fun finish(chatId: String, reply: Reply? = null) {
        if (reply != null) {
            if (chatId == workspace.selectedId) {
                reply.historyContent?.finalDocument = renderer.finish(reply.text)
            }
            putMessage(chatId, reply)
        }
        turns.remove(chatId)
        updateChat(chatId) { chat ->
            val atTail = chatId == workspace.selectedId && chat.anchor == Anchor()
            val workState = if (reply?.status == ReplyStatus.Completed && !atTail) SessionWorkState.Waiting
            else SessionWorkState.Idle
            chat.copy(
                interrupted = null,
                summary = chat.summary.copy(navigationStatus = workState.toNavigationStatus()),
            )
        }
        val messages = if (chatId == workspace.selectedId) history(selected) else null
        if (messages != null) {
            renderer.reset()
            renderOwner = null
            workspaceFiles.refresh(messages)
        }
        screen.update {
            val next = if (messages == null) it else {
                it.copy(
                    chat = selected.summary, messages = messages, live = null, request = remoteTurns?.request(chatId),
                    activity = remoteTurns?.activity(chatId) ?: Activity.Idle, fileGroups = workspaceFiles.groups(it.fileSearch),
                )
            }
            val updated = withProjectsSessions(next)
            if (reply != null && reply.status != ReplyStatus.Working) updated.collapseWork(reply.id) else updated
        }
        // The completed screen revision already owns the full final document. Keep the last
        // live revision until a new turn/chat replaces it, so collectors cannot see a blank handoff.
        save()
        if (messages != null && persistence != null) enqueue {
            if (screen.value.chat?.id == chatId) workspaceFiles.refreshCatalog(screen, persistence)
        }
    }

    internal fun event(command: () -> Unit) = enqueue { command() }

    private fun chat(id: String): AuroraChat = workspace.chats.first { it.summary.id == id }

    private fun changeNavigationStatus(id: String, status: NavigationStatus?): Boolean {
        if (chat(id).summary.navigationStatus == status) return false
        updateChat(id) { it.copy(summary = it.summary.copy(navigationStatus = status)) }
        return true
    }

    private fun updateChat(id: String, transform: (AuroraChat) -> AuroraChat) {
        workspace = workspace.copy(chats = workspace.chats.map { if (it.summary.id == id) transform(it) else it })
    }

    private fun history(chat: AuroraChat): List<Message> {
        val mockId = chat.mockId ?: chat.summary.id
        val initial = fixture.getOrPut(mockId) { AuroraMock.history(mockId) }
        val messages = if (expandedReplies.isEmpty()) chat.messages else chat.messages.map { source ->
            expandedReplies[chat.summary.id to source.id]?.takeIf { it.first === source }?.second ?: source
        }
        return if (messages.isEmpty()) initial else messages + initial
    }

    internal fun mockSessionId(id: String): String = chat(id).mockId ?: id

    private fun rememberDraft(draft: Draft) {
        val id = draft.chatId ?: return
        val previous = workspace.chats.firstOrNull { it.summary.id == id } ?: return
        val body = draft.references?.body
        if (previous.draft != draft.text || previous.messageBody != body) {
            updateChat(id) { it.editDraft(text = draft.text, body = body) }
        }
    }

    private fun save() {
        rememberDraft(editor.value)
        snapshots.schedule(workspace)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun restore(): AuroraWorkspace = try {
        val restored = snapshots.restore()
        projectsSessionsStore?.let { store ->
            store.dispatch(PanelAction.Load).getOrThrow()
            store.dispatch(SessionAction.Select(restored.selectedId)).getOrThrow()
        }
        restored
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // This workspace is temporary. Never replace a snapshot we could not read.
        snapshots.close()
        screen.update {
            it.copy(storageError = RestoreError)
        }
        snapshots.emptyWorkspace()
    }

    private fun AiStudioScreenState.stepsExpandedAfterInterim(reply: Reply?): Set<String> {
        if (reply?.status != ReplyStatus.Working) return expandedSteps
        val completed = reply.steps.filter { it.status != StepStatus.Current && it.result != null && it.events.isNotEmpty() }
        if (completed.isEmpty()) return expandedSteps

        val previous = live?.takeIf { it.id == reply.id }?.steps.orEmpty().associateBy { it.id }
        // Keep the stage's events visible when its result arrives. Later snapshots respect a manual collapse.
        val newlyCompleted = completed.filter { step ->
            val prior = previous[step.id]
            prior?.result == null || prior.status == StepStatus.Current
        }
        return if (newlyCompleted.isEmpty()) expandedSteps else expandedSteps + newlyCompleted.map { "${reply.id}/${it.id}" }
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    private companion object {
        const val RestoreError = "Не удалось восстановить диалоги. Изменения в этом сеансе не сохраняются."
        const val SaveError = "Не удалось сохранить диалог"
        const val WorkspaceSaveError = "Не удалось сохранить изменения в диалоге"
        const val SaveInterval = 150L
        const val MockTime = "14:04"
    }
}
