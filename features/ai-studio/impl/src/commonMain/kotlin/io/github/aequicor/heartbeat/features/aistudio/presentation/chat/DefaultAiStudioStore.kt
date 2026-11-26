@file:Suppress("TooManyFunctions")

package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ChatTurnInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ImportChatAttachmentUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatTurn
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
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
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val editor = MutableStateFlow(Draft())
    private val stream = MutableStateFlow(LiveText())
    private var renderOwner: AuroraTurn? = null
    private val renderer = ReplyRenderStream(
        schedule = { milliseconds, action -> scope.launch { delay(milliseconds); enqueue { action() } } },
        publish = { stream.value = it },
    )
    private val fixture = mutableMapOf<String, List<Message>>()
    private val turns = mutableMapOf<String, AuroraTurn>()
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
        when (action) {
            is AiStudioAction.EditDraft -> {
                // Keep text feedback immediate. Only a small editor snapshot is touched on this path.
                editor.update { if (it.chatId == action.chatId) it.copy(text = action.text) else it }
                saveRequests.trySend(Unit)
            }
            AiStudioAction.Send -> screen.value.chat?.id?.let(::submitChatDraft)
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
            AiStudioAction.CancelTurn, is AiStudioAction.CancelChatTurn, is AiStudioAction.AnswerPermission,
            -> Unit
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
                enginePicker?.close()
                screen.update { it.copy(sessionBootstrap = null) }
            }
            is AiStudioAction.EnginePicker -> handleEnginePicker(action)
            is AiStudioAction.BootstrapProject -> {
                val project = screen.value.sessionBootstrap?.project
                updateBootstrapProject(screen, sessionBootstrap, defaultDispatcher, action)
                val bootstrap = screen.value.sessionBootstrap
                if (bootstrap != null && bootstrap.project != project) {
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
            is AiStudioAction.SubmitSessionBootstrap ->
                submitSessionBootstrap(screen, enginePicker, action, ::openBootstrappedSession)
            is AiStudioAction.RenameProject -> updateProjectsSessions(ProjectAction.Rename(action.id, action.title))
            is AiStudioAction.DeleteProject -> updateProjectsSessions(ProjectAction.Delete(action.id))
            is AiStudioAction.NewProjectChat -> newChat(action.project)
            is AiStudioAction.SetScenario -> updateSummary { it.copy(scenario = action.scenario) }
            is AiStudioAction.SelectSecurityMode -> selectSecurityMode(action)
            is AiStudioAction.SetAccess -> updateSummary { it.copy(access = action.access) }
            is AiStudioAction.SetEffort -> updateSummary { it.copy(effort = action.effort) }
            is AiStudioAction.Attach -> if (importAttachment == null) attach(action.fileId) else Unit
            is AiStudioAction.ImportAttachment -> importAttachments(action.draftId) { importFiles ->
                importFiles(listOf(action.reference))
            }
            is AiStudioAction.ImportAttachments -> importAttachments(action.draftId, action.source)
            is AiStudioAction.AttachmentPickerFailed -> reportAttachmentError(action.draftId)
            is AiStudioAction.Detach -> detach(action.fileId)
            is AiStudioAction.Retry -> retry(action.id)
            is AiStudioAction.EditMessage -> editMessage(action.id)
            is AiStudioAction.InteractWithRequest, is AiStudioAction.Choose,
            is AiStudioAction.EditAnswer, is AiStudioAction.MoveQuestion,
            is AiStudioAction.SkipQuestion, is AiStudioAction.SubmitAnswers, is AiStudioAction.DeclineQuestionnaire -> {
                val id = workspace.selectedId
                val remote = remoteTurns
                if (remote?.isRunning(id) == true) {
                    remote.answerQuestionnaire(id, action)
                    screen.update { if (it.chat?.id == id) it.copy(request = remote.request(id)) else it }
                } else turns[id]?.answer(action)
            }
            is AiStudioAction.ToggleFiles -> workspaceFiles.toggleFiles(screen, action.visible, persistence)
            is AiStudioAction.ShowFileSearch -> screen.update { workspaceFiles.setSearchVisible(it, action.visible) }
            is AiStudioAction.SearchFiles -> screen.update { workspaceFiles.search(it, action.text) }
            is AiStudioAction.OpenFile -> openViewer(workspaceFiles.find(action.id))
            is AiStudioAction.OpenResult -> openViewer(findMessage(action.id).let { (it as? Reply)?.result })
            is AiStudioAction.OpenArtifact -> findArtifact(action.replyId, action.fileId)?.let { openViewer(it) }
            is AiStudioAction.SaveArtifact -> if (screen.value.artifactToSave == null) {
                val file = findArtifact(action.replyId, action.fileId) ?: return
                val opened = screen.readStored { file.withContent(persistence) } ?: return
                screen.update { it.copy(artifactToSave = opened) }
            }
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
            is AiStudioAction.SetWorkExpanded -> if (screen.value.chat != null) {
                screen.setWorkExpanded(selected, action, persistence, expandedReplies)
            }
        }
    }

    private fun requestTurnCancellation(id: String) {
        val remote = remoteTurns
        if (remote?.requestCancellation(id) != true) {
            enqueue { turns[id]?.stop() }
            return
        }
        screen.update {
            if (it.chat?.id == id && remote.isStopping(id)) it.copy(activity = Activity.Stopping, request = null) else it
        }
    }

    private suspend fun answerPermission(id: String, action: AiStudioAction.AnswerPermission) {
        if (remoteTurns?.isRunning(id) == true) remoteTurns.answer(id, action.requestId, action.allow)
        else turns[id]?.answer(action)
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
        if (screen.value.chat?.id == id && target.messages === entry.messages) {
            screen.update { withProjectsSessions(it.copy(chat = target.summary, sessionBootstrap = null)) }
            if (target !== loaded) save()
        } else {
            showChat(target)
        }
    }

    private suspend fun loadOlderMessages() {
        val chat = selected
        val loaded = screen.readStored { persistence?.loadOlder(chat) } ?: return
        updateChat(chat.summary.id) { loaded }
        val messages = history(loaded)
        workspaceFiles.refresh(messages)
        screen.update {
            it.copy(
                messages = messages, olderMessagesBefore = loaded.olderBefore,
                fileGroups = workspaceFiles.groups(it.fileSearch),
            )
        }
    }

    private suspend fun showChat(target: AuroraChat) {
        val id = target.summary.id
        val before = editor.getAndUpdate { Draft(id, target.draft) }
        rememberDraft(before)
        val messages = history(target)
        val turn = turns[id]
        requestRender(turn)
        val live = remoteTurns?.reply(id) ?: turn?.reply
        if (turn == null && live != null) renderer.update(id, live.id, live.text)
        val anchorIndex = messages.indexOfFirst { it.id == target.anchor.messageId }
        val restoredIndex = when {
            live?.id == target.anchor.messageId && live != null -> 0
            anchorIndex < 0 -> 0
            else -> anchorIndex + if (live == null) 0 else 1
        }
        workspaceFiles.setStored(listOf())
        workspaceFiles.refresh(messages)
        screen.update {
            val next = it.copy(
                loading = false,
                chat = target.summary,
                messages = messages,
                olderMessagesBefore = target.olderBefore,
                live = live,
                request = remoteTurns?.request(id) ?: turn?.request,
                activity = remoteTurns?.activity(id) ?: turn?.activity ?: Activity.Idle,
                restoreAnchor = target.anchor,
                restoreIndex = restoredIndex,
                openedHistory = AiStudioScreenState.OpenedHistory(live?.id ?: messages.firstOrNull()?.id),
                fileGroups = workspaceFiles.groups(it.fileSearch),
                attachments = target.attachments,
                viewer = null,
                subagents = workspace.subagents(id),
                sessionBootstrap = null,
            )
            withProjectsSessions(next)
        }
        if (screen.value.filesVisible) workspaceFiles.toggleFiles(screen, true, persistence)
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
        screen.update {
            withProjectsSessions(it.copy(loading = false, sessionBootstrap = bootstrap, filesVisible = false, viewer = null))
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
        updateChat(id) { chat ->
            chat.copy(
                draft = action.prompt,
                attachments = files,
                summary = chat.summary.copy(
                    configuration = configuration,
                    scenario = action.scenario,
                    access = action.access,
                    effort = action.effort ?: chat.summary.effort,
                ),
            )
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
        if (clearedChat) editor.value = Draft()
        for (id in removed) {
            remoteTurns?.discard(id)
            turns.remove(id)?.stop()
        }
        workspace = workspace.withoutChats(removed)
        if (clearedChat) {
            requestRender(null)
            screen.update { current ->
                withProjectsSessions(AiStudioScreenState(loading = false, storageError = current.storageError))
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

    private fun importAttachments(id: String, source: AttachmentImportSource) {
        val useCase = importAttachment ?: return
        if (!hasAttachmentDraft(id) || id in screen.value.importingAttachmentsFor) return
        screen.update {
            it.copy(
                importingAttachmentsFor = it.importingAttachmentsFor + id,
                attachmentErrorFor = it.attachmentErrorFor.takeUnless { failedId -> failedId == id },
            )
        }
        scope.launch {
            try {
                source.withReferences { references ->
                    for (reference in references) {
                        val result = withContext(defaultDispatcher) { useCase(reference) }
                        enqueue {
                            result.fold(
                                onSuccess = { addAttachment(id, it.display()) },
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

    private fun addAttachment(draftId: String, file: File) {
        if (screen.value.sessionBootstrap?.id == draftId) {
            screen.update { state ->
                val bootstrap = state.sessionBootstrap ?: return@update state
                state.copy(sessionBootstrap = bootstrap.copy(attachments = bootstrap.attachments + file))
            }
            return
        }
        if (!hasAttachmentDraft(draftId)) return
        updateChat(draftId) { it.copy(attachments = it.attachments + file) }
        val files = chat(draftId).attachments
        screen.update { if (it.chat?.id == draftId) it.copy(attachments = files) else it }
        save()
    }

    private fun hasAttachmentDraft(id: String): Boolean =
        screen.value.sessionBootstrap?.id == id ||
            workspace.chats.any { it.summary.id == id && it.payloadLoaded && id !in workspace.archivedChats }

    private fun reportAttachmentError(id: String) {
        if (hasAttachmentDraft(id)) screen.update { it.copy(attachmentErrorFor = id) }
    }

    private fun detach(fileId: String) {
        val bootstrap = screen.value.sessionBootstrap
        if (bootstrap == null) {
            val chat = screen.value.chat ?: return
            attachments(this.chat(chat.id).attachments.filterNot { it.id == fileId })
        } else {
            screen.update {
                it.copy(sessionBootstrap = it.sessionBootstrap?.let { current ->
                    current.copy(attachments = current.attachments.filterNot { file -> file.id == fileId })
                })
            }
        }
    }

    private fun attachments(value: List<File>) {
        updateChat(workspace.selectedId) { it.copy(attachments = value) }
        screen.update { it.copy(attachments = value) }
        save()
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
        val activeTurn = chatId in turns || remoteTurns?.isRunning(chatId) == true
        if (chatId in screen.value.importingAttachmentsFor) return
        if (screen.value.isPromptBlockedBySecurityMode(chat.summary)) return
        if (draft.chatId != chatId || !ChatPrompt.canSend(text, chat.attachments.isNotEmpty(), activeTurn)) return
        val remote = remoteTurns
        if (remote != null) {
            sendToEngine(remote, draft, text, chat)
            return
        }
        val summary = summaryForPrompt(chat, text) ?: return
        editor.compareAndSet(draft, draft.copy(text = ""))
        updateChat(chatId) { it.copy(attachments = listOf(), summary = summary) }
        val user = User(
            nextId(), MockTime, text, chat.attachments, Delivery.Sending,
            scenario = chat.summary.scenario, access = chat.summary.access,
        )
        newTurn(user).sendPrompt()
    }

    private suspend fun sendToEngine(remote: RemoteChatTurns, draft: Draft, text: String, chat: AuroraChat) {
        val id = chat.summary.id
        screen.update { it.copy(submittingDrafts = it.submittingDrafts + id) }
        try {
            val files = screen.readStored { chat.attachments.map { it.withContent(persistence) } } ?: return
            startEngineTurn(remote, id, RemoteChatTurns.Submission.New(text, files) { saveSubmittedDraft(draft, chat) })
        } finally {
            if (!remote.isRunning(id)) finishDraftSubmission(id)
        }
    }

    private fun startEngineTurn(remote: RemoteChatTurns, id: String, submission: RemoteChatTurns.Submission) {
        if (remote.isRunning(id)) return
        remote.start(
            id, submission,
            render = { update ->
                if (workspace.chats.any { it.summary.id == id }) renderRemoteTurn(id, update)
            },
            complete = { outcome ->
                if (submission is RemoteChatTurns.Submission.New) finishDraftSubmission(id)
                if (workspace.chats.any { it.summary.id == id }) {
                    if (outcome?.exceptionOrNull() is StudioRepositoryException) reportChatSaveError(id)
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

    @Throwable(CancellationException::class)
    private suspend fun saveSubmittedDraft(draft: Draft, submittedChat: AuroraChat): Result<Unit> {
        val id = submittedChat.summary.id
        if (workspace.chats.none { it.summary.id == id }) throw CancellationException("Chat was removed")
        rememberDraft(editor.value)
        val submittedFiles = submittedChat.attachments.toSet()
        val next = workspace.copy(chats = workspace.chats.map { chat ->
            if (chat.summary.id == id) {
                chat.copy(
                    draft = if (chat.draft == draft.text) "" else chat.draft,
                    attachments = chat.attachments - submittedFiles,
                    persistedThroughDomain = true,
                )
            } else {
                chat
            }
        })
        return snapshots.saveNow(next).onSuccess {
            workspace = next
            editor.compareAndSet(draft, draft.copy(text = ""))
            finishDraftSubmission(id)
        }
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

    private fun renderRemoteTurn(id: String, turn: ChatTurn) {
        if (!chat(id).persistedThroughDomain) updateChat(id) { it.copy(persistedThroughDomain = true) }
        val visible = workspace.selectedId == id && screen.value.chat?.id == id
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

    private fun retry(id: String) {
        if (screen.value.isPromptBlockedBySecurityMode(selected.summary)) return
        val message = findMessage(id)
        if ((message as? User)?.retryAllowed == false || (message as? Reply)?.retryAllowed == false) return
        remoteTurns?.let { remote ->
            if (message is User && message.delivery == Delivery.Failed && !message.answers) {
                startEngineTurn(remote, workspace.selectedId, RemoteChatTurns.Submission.Retry(id))
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
        val opened = screen.readStored { file?.withContent(persistence) }
        screen.update { it.copy(viewer = opened) }
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
                    chat = selected.summary, messages = messages, live = null, request = null, activity = Activity.Idle,
                    fileGroups = workspaceFiles.groups(it.fileSearch),
                )
            }
            val updated = withProjectsSessions(next)
            if (reply != null && reply.status != ReplyStatus.Working) updated.collapseWork(reply.id) else updated
        }
        // The completed screen revision already owns the full final document. Keep the last
        // live revision until a new turn/chat replaces it, so collectors cannot see a blank handoff.
        save()
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
        if (chat(id).draft != draft.text) updateChat(id) { it.copy(draft = draft.text) }
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
