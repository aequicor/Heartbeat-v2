@file:Suppress("TooManyFunctions")

package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.SessionBootstrapInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectsSessionsChange
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.SessionBootstrapState
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.AuroraMock
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.AuroraTurn
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions.ProjectsSessionsStoreImpl
import io.github.aequicor.heartbeat.features.aistudio.presentation.rich.ReplyRenderStream
import io.github.aequicor.heartbeat.features.aistudio.presentation.sessionbootstrap.loadBootstrapProjects
import io.github.aequicor.heartbeat.features.aistudio.presentation.sessionbootstrap.sessionTitleFromPrompt
import io.github.aequicor.heartbeat.features.aistudio.presentation.sessionbootstrap.submitSessionBootstrap
import io.github.aequicor.heartbeat.features.aistudio.presentation.sessionbootstrap.updateBootstrapProject
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.StudioWorkspaceStorage
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.toNavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.WorkspaceFiles
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.WorkspaceSnapshots
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.collapseWork
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.deletedChatIds
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.renameProject
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.subagents
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.withProjectsSessions
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.withReadResult
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.withoutChats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : AiStudioStore {

    override val projectsSessionsStore: ProjectsSessionsStore? = catalog?.let {
        ProjectsSessionsStoreImpl(it, catalogChanges ?: emptyFlow(), scope)
    }

    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private val snapshots = WorkspaceSnapshots(preferences, persistence, useMockWorkspace = catalog == null)
    private val screen = MutableStateFlow(AiStudioScreenState(fileChoices = AuroraMock.inputs))
    private val editor = MutableStateFlow(Draft())
    private val stream = MutableStateFlow(LiveText())
    private var renderOwner: AuroraTurn? = null
    private val renderer = ReplyRenderStream(
        schedule = { milliseconds, action -> scope.launch { delay(milliseconds); enqueue { action() } } },
        publish = { stream.value = it },
    )
    private val fixture = mutableMapOf<String, List<Message>>()
    private val turns = mutableMapOf<String, AuroraTurn>()
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
                        screen.update { if (it.storageError == SaveError) it.copy(storageError = null) else it }
                    },
                    onFailure = ::reportStorageError,
                )
            }
            if (workspace.chats.none { it.summary.id == workspace.selectedId }) {
                val fallback = workspace.activeChats().firstOrNull()?.id ?: workspace.chats.firstOrNull()?.summary?.id
                if (fallback != null) workspace = workspace.copy(selectedId = fallback)
            }
            if (catalog != null || workspace.chats.isEmpty()) {
                screen.update { withProjectsSessions(it.copy(loading = false)) }
            } else {
                openChat(workspace.selectedId, acknowledgeResult = false)
            }
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

    override fun dispatch(action: AiStudioAction) {
        when (action) {
            is AiStudioAction.EditDraft -> {
                // Keep text feedback immediate. Only a small editor snapshot is touched on this path.
                editor.update { if (it.chatId == action.chatId) it.copy(text = action.text) else it }
                saveRequests.trySend(Unit)
            }
            AiStudioAction.Send -> {
                val submitted = editor.value
                enqueue { send(submitted) }
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
            is AiStudioAction.EditDraft, AiStudioAction.Send -> Unit
            is AiStudioAction.ReadAt -> readAt(action)
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
            AiStudioAction.LoadBootstrapProjects -> loadBootstrapProjects(screen, catalog)
            AiStudioAction.DismissSessionBootstrap -> screen.update { it.copy(sessionBootstrap = null) }
            is AiStudioAction.BootstrapProject -> updateBootstrapProject(screen, sessionBootstrap, action)
            is AiStudioAction.SubmitSessionBootstrap ->
                submitSessionBootstrap(screen, sessionBootstrap, action, ::openBootstrappedSession)
            is AiStudioAction.RenameProject -> updateProjectsSessions(ProjectAction.Rename(action.id, action.title))
            is AiStudioAction.DeleteProject -> updateProjectsSessions(ProjectAction.Delete(action.id))
            is AiStudioAction.NewProjectChat -> newChat(action.project)
            is AiStudioAction.SetScenario -> updateSummary { it.copy(scenario = action.scenario) }
            is AiStudioAction.SetAccess -> updateSummary { it.copy(access = action.access) }
            is AiStudioAction.SetEffort -> updateSummary { it.copy(effort = action.effort) }
            is AiStudioAction.Attach -> attach(action.fileId)
            is AiStudioAction.Detach -> attachments(selected.attachments.filterNot { it.id == action.fileId })
            AiStudioAction.CancelTurn -> turns[workspace.selectedId]?.stop()
            is AiStudioAction.Retry -> retry(action.id)
            is AiStudioAction.EditMessage -> editMessage(action.id)
            is AiStudioAction.InteractWithRequest, is AiStudioAction.Choose,
            is AiStudioAction.EditAnswer, is AiStudioAction.MoveQuestion,
            is AiStudioAction.SkipQuestion, is AiStudioAction.SubmitAnswers, is AiStudioAction.AnswerPermission,
            -> turns[workspace.selectedId]?.answer(action)
            is AiStudioAction.ToggleFiles -> screen.update { it.copy(filesVisible = action.visible) }
            is AiStudioAction.ShowFileSearch -> screen.update { workspaceFiles.setSearchVisible(it, action.visible) }
            is AiStudioAction.SearchFiles -> screen.update { workspaceFiles.search(it, action.text) }
            is AiStudioAction.OpenFile -> openViewer(workspaceFiles.find(action.id))
            is AiStudioAction.OpenResult -> openViewer(findMessage(action.id).let { (it as? Reply)?.result })
            AiStudioAction.CloseViewer -> openViewer(null)
            is AiStudioAction.ToggleWork -> screen.update { it.copy(expandedWork = it.expandedWork.toggle(action.id)) }
            is AiStudioAction.ToggleStep -> screen.update {
                it.copy(expandedSteps = it.expandedSteps.toggle("${action.replyId}/${action.stepId}"))
            }
            is AiStudioAction.ToggleWorkEvent -> screen.update {
                val key = "${action.replyId}/${action.stepId}/${action.eventId}"
                it.copy(expandedEvents = it.expandedEvents.toggle(key))
            }
        }
    }

    private suspend fun openChat(id: String, acknowledgeResult: Boolean = true) {
        val entry = workspace.chats.firstOrNull { it.summary.id == id } ?: return
        val loaded = loadStoredChat(entry) ?: return
        val target = if (acknowledgeResult) loaded.withReadResult() else loaded
        if (target !== entry) updateChat(id) { target }
        projectsSessionsStore?.dispatch(SessionAction.Select(id))?.getOrThrow()
        projectsSessionsStore?.dispatch(PanelAction.CollapsePanel)?.getOrThrow()
        workspace = workspace.copy(selectedId = id)
        if (screen.value.chat?.id == id) {
            screen.update { withProjectsSessions(it.copy(chat = target.summary, sessionBootstrap = null)) }
            if (target !== loaded) save()
        } else {
            showChat(target)
        }
    }

    private fun loadStoredChat(chat: AuroraChat): AuroraChat? = try {
        persistence?.load(chat) ?: chat
    } catch (_: StudioRepositoryException) {
        screen.update { it.copy(loading = false, storageError = RestoreError) }
        null
    }

    private fun showChat(target: AuroraChat) {
        val id = target.summary.id
        val before = editor.getAndUpdate { Draft(id, target.draft) }
        rememberDraft(before)
        val messages = history(target)
        val turn = turns[id]
        requestRender(turn)
        val anchorIndex = messages.indexOfFirst { it.id == target.anchor.messageId }
        val restoredIndex = when {
            turn?.reply?.id == target.anchor.messageId && turn?.reply != null -> 0
            anchorIndex < 0 -> 0
            else -> anchorIndex + if (turn?.reply == null) 0 else 1
        }
        workspaceFiles.refresh(messages)
        screen.update {
            val next = it.copy(
                loading = false,
                chat = target.summary,
                messages = messages,
                live = turn?.reply,
                request = turn?.request,
                activity = turn?.activity ?: Activity.Idle,
                restoreAnchor = target.anchor,
                restoreIndex = restoredIndex,
                fileGroups = workspaceFiles.groups(it.fileSearch),
                attachments = target.attachments,
                viewer = null,
                subagents = workspace.subagents(id),
                sessionBootstrap = null,
            )
            withProjectsSessions(next)
        }
        save()
    }

    private suspend fun selectCatalogSession(id: String) {
        val store = projectsSessionsStore ?: return
        store.dispatch(SessionAction.Select(id)).getOrThrow()
        screen.update(::withProjectsSessions)
    }

    private suspend fun newChat(project: String? = null) {
        val current = screen.value.chat
        if (catalog != null) {
            val id = createStoredChat(project ?: projectsSessionsStore?.state?.value?.selectedSessionId?.let {
                catalog.findSession(SessionId.parse(it))?.projectId?.value?.toString()
            }) ?: return
            selectCatalogSession(id)
            save()
            return
        }
        val chat = Chat(nextId(), Chat.UntitledTitle, project ?: current?.project ?: Chat.UngroupedProjectKey)
        workspace = workspace.insert(AuroraChat(chat))
        openChat(chat.id)
    }

    private suspend fun createStoredChat(rawProjectId: String?): String? {
        val projectId = if (rawProjectId == null) {
            ProjectId.Ungrouped
        } else {
            val storedId = workspace.chats.firstOrNull { it.summary.project == rawProjectId }?.summary?.projectId
            runCatching { ProjectId.parse(storedId ?: rawProjectId) }.getOrNull() ?: return null
        }
        val result = sessionBootstrap?.createSession(projectId, Chat.UntitledTitle)
        return when (result) {
            is SessionBootstrapResult.Created -> addStoredSession(result.session, result.project)
            SessionBootstrapResult.ProjectUnavailable, null -> null
        }
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
        projectsSessionsStore?.dispatch(PanelAction.CollapsePanel)?.getOrThrow()
        val bootstrap = SessionBootstrapState(project, projectTitle = title, id = nextId())
        screen.update {
            withProjectsSessions(it.copy(sessionBootstrap = bootstrap, filesVisible = false, viewer = null))
        }
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
    ) {
        val id = addStoredSession(result.session, result.project)
        updateChat(id) { chat ->
            chat.copy(
                draft = action.prompt,
                summary = chat.summary.copy(
                    scenario = action.scenario,
                    access = action.access,
                    effort = action.effort ?: chat.summary.effort,
                ),
            )
        }
        openChat(id, acknowledgeResult = false)
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
        for (id in removed) turns.remove(id)?.stop()
        workspace = workspace.withoutChats(removed)
        if (clearedChat) {
            requestRender(null)
            screen.update { current ->
                withProjectsSessions(AiStudioScreenState(loading = false, storageError = current.storageError))
            }
        }
    }

    private fun updateSummary(transform: (Chat) -> Chat) {
        updateChat(workspace.selectedId) { it.copy(summary = transform(it.summary)) }
        screen.update { withProjectsSessions(it.copy(chat = selected.summary)) }
        save()
    }

    private fun attach(id: String) {
        val file = AuroraMock.inputs.firstOrNull { it.id == id } ?: return
        if (selected.attachments.none { it.id == id }) attachments(selected.attachments + file)
    }

    private fun attachments(value: List<File>) {
        updateChat(workspace.selectedId) { it.copy(attachments = value) }
        screen.update { it.copy(attachments = value) }
        save()
    }

    private suspend fun send(draft: Draft) {
        val chat = selected
        val chatId = chat.summary.id
        val text = draft.text.trim()
        val emptyPrompt = text.isEmpty() && chat.attachments.isEmpty()
        if (draft.chatId != chatId || chatId in turns || emptyPrompt) {
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

    private suspend fun summaryForPrompt(chat: AuroraChat, text: String): Chat? {
        if (!chat.summary.isUntitled) return chat.summary
        val firstLine = sessionTitleFromPrompt(text)
        val title = firstLine.ifBlank { chat.attachments.first().name }
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
        val message = findMessage(id)
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

    private fun editMessage(id: String) {
        val message = findMessage(id) as? User ?: return
        if (message.answers) return
        editor.update { it.copy(text = message.text) }
        attachments(message.files)
    }

    private fun openViewer(file: File?) {
        screen.update { it.copy(viewer = file) }
    }

    private fun findMessage(id: String?): Message? = screen.value.messages.firstOrNull { it.id == id }

    internal fun nextId(): String {
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
        if (structural) {
            val status = turn.workState.toNavigationStatus()
            if (chat(chatId).summary.navigationStatus != status) {
                updateChat(chatId) { it.copy(summary = it.summary.copy(navigationStatus = status)) }
            }
        }
        if (chatId != workspace.selectedId && structural) screen.update(::withProjectsSessions)
        if (chatId == workspace.selectedId) {
            requestRender(turn)
            val messages = if (structural) history(selected) else screen.value.messages
            if (structural) workspaceFiles.refresh(messages)
            screen.update {
                val next = it.copy(
                    chat = selected.summary,
                    messages = messages, live = turn.reply, request = turn.request, activity = turn.activity,
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
            if (reply?.status == ReplyStatus.Completed) updated.collapseWork(reply.id) else updated
        }
        // The completed screen revision already owns the full final document. Keep the last
        // live revision until a new turn/chat replaces it, so collectors cannot see a blank handoff.
        save()
    }

    internal fun event(command: () -> Unit) = enqueue { command() }

    private fun chat(id: String): AuroraChat = workspace.chats.first { it.summary.id == id }

    private fun updateChat(id: String, transform: (AuroraChat) -> AuroraChat) {
        workspace = workspace.copy(chats = workspace.chats.map { if (it.summary.id == id) transform(it) else it })
    }

    private fun history(chat: AuroraChat): List<Message> {
        val mockId = chat.mockId ?: chat.summary.id
        val initial = fixture.getOrPut(mockId) { AuroraMock.history(mockId) }
        return if (chat.messages.isEmpty()) initial else chat.messages + initial
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

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    private companion object {
        const val SaveError = "Не удалось сохранить диалог"
        const val RestoreError = "Не удалось восстановить диалоги. Изменения в этом сеансе не сохраняются."
        const val SaveInterval = 150L
        const val MockTime = "14:04"
    }
}
