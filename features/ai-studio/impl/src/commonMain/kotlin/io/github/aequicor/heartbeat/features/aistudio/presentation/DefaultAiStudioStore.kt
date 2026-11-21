@file:Suppress("TooManyFunctions")

package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStore
import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aistudio.data.StudioCatalog
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraNavigation
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Delivery
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.FileGroup
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.AuroraMock
import io.github.aequicor.heartbeat.features.aistudio.presentation.mock.AuroraTurn
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraNavigation.MorePrefix
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraNavigation.ProjectPrefix
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.filterFiles
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.navigation
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.prepareFileGroups
import io.github.aequicor.heartbeat.features.aistudio.presentation.rich.ReplyMarkdownParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Workspace commands and mock events are serialized on the component's background scope. */
internal class DefaultAiStudioStore(
    preferences: PreferencesStore,
    private val scope: CoroutineScope,
    private val catalog: StudioCatalog? = null,
    private val engineSessions: StudioEngineSessions? = null,
) : AiStudioStore {
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val selectedSession: Session? get() = engineSessions?.selected
    private val storage = StudioWorkspaceStorage(preferences, catalog)
    private val loadedChats = mutableSetOf<String>()
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)
    private val snapshots = Channel<AuroraWorkspace>(Channel.CONFLATED)
    private val screen = MutableStateFlow(AiStudioScreenState(fileChoices = AuroraMock.inputs))
    private val editor = MutableStateFlow(Draft())
    private val stream = MutableStateFlow(LiveText())
    private data class RenderRequest(val turn: AuroraTurn, val id: String, val text: String)
    private val renderRequests = Channel<RenderRequest>(Channel.CONFLATED)
    private var requested: RenderRequest? = null
    private val fixture = mutableMapOf<String, List<Message>>()
    private val turns = mutableMapOf<String, AuroraTurn>()
    private lateinit var workspace: AuroraWorkspace
    private val selected get() = chat(workspace.selectedId)
    private var allFiles = listOf<File>()
    private var allFileGroups = listOf<FileGroup>()

    override val screenState = screen.asStateFlow()
    override val draft = editor.asStateFlow()
    override val liveText = stream.asStateFlow()

    init {
        if (engineSessions != null) scope.launch {
            engineSessions.availableEngines.collect { keys ->
                screen.update { it.copy(availableEngines = keys.associate { key -> key.id to key.name }) }
            }
        }
        scope.launch {
            var owner: AuroraTurn? = null
            var parser = ReplyMarkdownParser()
            for (first in renderRequests) {
                // Batch complete source snapshots before preparing spans. Conflation never drops text.
                if (first.turn === owner) delay(RenderInterval)
                val request = renderRequests.tryReceive().getOrNull() ?: first
                if (request.turn !== owner) parser = ReplyMarkdownParser()
                owner = request.turn
                val snapshot = parser.renderLiveText(request.id, request.text)
                enqueue {
                    // Completed work can catch up during a token burst. A switched, replaced or
                    // stopped turn must never overwrite the active reply.
                    val latest = requested
                    if (latest === request ||
                        (latest?.turn === request.turn && latest.text.startsWith(request.text))
                    ) stream.value = snapshot
                }
            }
        }
        scope.launch {
            workspace = restore()
            if (workspace.chats.none { it.summary.id == workspace.selectedId }) {
                val fallback = workspace.activeChats().firstOrNull()?.id.orEmpty()
                workspace = workspace.copy(selectedId = fallback)
            }
            if (workspace.selectedId.isEmpty()) publishCatalog() else openChat(workspace.selectedId)
            if (catalog != null && screen.value.storageError == null) {
                scope.launch { catalog.changes.collect { enqueue(::refreshCatalog) } }
            }
            processCommands()
        }
        scope.launch {
            while (saveRequests.receiveCatching().isSuccess) {
                delay(SaveInterval)
                enqueue(::save)
            }
        }
        scope.launch {
            for (snapshot in snapshots) persist(snapshot)
        }
    }

    @Suppress("TooGenericExceptionCaught") // A failed engine or database operation must not stop the command loop.
    private suspend fun processCommands() {
        for (command in commands) {
            try {
                command()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                screen.update { it.copy(storageError = error.message ?: SaveError) }
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
                enqueue {
                    if (submitted.chatId != null && submitted.chatId == workspace.selectedId) {
                        if (selected.summary.nativeId == null) send(submitted) else sendNative(submitted)
                    }
                }
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
            is AiStudioAction.OpenChat -> selectChat(action.id)
            is AiStudioAction.ArchiveChat -> archiveChat(action.id)
            AiStudioAction.NewChat -> newChat()
            is AiStudioAction.SearchChats -> showChats(action.text)
            is AiStudioAction.ShowChatSearch -> showChatSearch(action.visible)
            is AiStudioAction.ToggleNavigation -> toggleNavigation(action.id)
            is AiStudioAction.NavigationFocusHandled -> screen.update {
                if (it.navigationFocusId == action.id) it.copy(navigationFocusId = null) else it
            }
            is AiStudioAction.OpenProject -> {
                val projectId = catalog?.openProject(action.workingDirectory, action.name)
                workspace = workspace.copy(activeProjectId = projectId)
                refreshCatalog()
            }
            is AiStudioAction.NewProjectChat -> newChat(action.project, action.engineId)
            is AiStudioAction.SetScenario -> updateSummary { it.copy(scenario = action.scenario) }
            is AiStudioAction.SetAccess -> updateSummary { it.copy(access = action.access) }
            is AiStudioAction.SetEffort -> updateSummary { it.copy(effort = action.effort) }
            is AiStudioAction.Attach -> attach(action.fileId)
            is AiStudioAction.Detach -> attachments(selected.attachments.filterNot { it.id == action.fileId })
            AiStudioAction.CancelTurn ->
                selectedSession?.dispatch(SessionEvent.Cancel) ?: turns[workspace.selectedId]?.stop()
            is AiStudioAction.Retry -> retry(action.id)
            is AiStudioAction.EditMessage -> editMessage(action.id)
            is AiStudioAction.InteractWithRequest, is AiStudioAction.Choose,
            is AiStudioAction.EditAnswer, is AiStudioAction.MoveQuestion,
            is AiStudioAction.SkipQuestion, is AiStudioAction.SubmitAnswers,
            -> turns[workspace.selectedId]?.answer(action)
            is AiStudioAction.AnswerPermission -> {
                val session = selectedSession
                if (session == null) turns[workspace.selectedId]?.answer(action) else session.answer(action)
            }
            is AiStudioAction.ToggleChats -> screen.update { it.copy(chatsVisible = action.visible) }
            is AiStudioAction.ToggleFiles -> screen.update { it.copy(filesVisible = action.visible) }
            is AiStudioAction.ShowFileSearch -> showFileSearch(action.visible)
            is AiStudioAction.SearchFiles -> showFiles(action.text)
            is AiStudioAction.OpenFile -> openViewer(allFiles.firstOrNull { it.id == action.id })
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

    private fun openChat(id: String) {
        val entry = workspace.chats.firstOrNull { it.summary.id == id } ?: return
        if (catalog != null && id !in loadedChats) {
            val stored = catalog.details(id)?.let { Json.decodeFromString<AuroraChat>(it).recover() }
            if (stored != null) {
                val current = entry.summary
                val summary = stored.summary.copy(title = current.title, project = current.project,
                    projectId = current.projectId, engineId = current.engineId, nativeId = current.nativeId)
                updateChat(id) { stored.copy(summary = summary) }
            }
            loadedChats += id
        }
        val target = chat(id)
        if (screen.value.chat?.id == id) {
            screen.update { it.copy(chatsVisible = false, navigationFocusId = null) }
            return
        }
        val before = editor.getAndUpdate { Draft(id, target.draft) }
        rememberDraft(before)
        workspace = workspace.copy(selectedId = id, activeProjectId = target.summary.projectId)
        val messages = history(target)
        val turn = turns[id]
        requestRender(turn)
        val anchorIndex = messages.indexOfFirst { it.id == target.anchor.messageId }
        val restoredIndex = when {
            turn?.reply?.id == target.anchor.messageId && turn?.reply != null -> 0
            anchorIndex < 0 -> 0
            else -> anchorIndex + if (turn?.reply == null) 0 else 1
        }
        refreshFiles(messages)
        screen.update {
            it.copy(
                loading = false, chat = target.summary, chats = matchingChats(it.chatSearch), messages = messages,
                navigation = sidebar(it.chatSearch), navigationFocusId = null,
                live = turn?.reply, request = turn?.request, activity = turn?.activity ?: Activity.Idle,
                restoreAnchor = target.anchor, restoreIndex = restoredIndex,
                fileGroups = allFileGroups.filterFiles(it.fileSearch),
                attachments = target.attachments, chatsVisible = false, viewer = null,
                subagents = subagents(id),
            )
        }
        save()
    }

    private suspend fun selectChat(id: String) {
        val chat = workspace.chats.find { it.summary.id == id }?.summary ?: return
        if (screen.value.chat?.id != id) {
            engineSessions?.clearSelection()
        }
        openChat(id)
        if (chat.engineId != null && selectedSession == null) {
            val sessions = requireNotNull(engineSessions)
            val opened = sessions.open(requireNotNull(chat.projectId), chat.engineId, chat.nativeId)
            attachSession(opened)
        }
    }

    private fun attachSession(opened: StudioEngineSessions.Opened) {
        requireNotNull(engineSessions).select(opened, scope) { session, state ->
            enqueue { if (selectedSession === session) screen.update { it.withSession(state) } }
        }
    }

    private fun refreshCatalog() {
        if (catalog != null) workspace = catalog.load(workspace)
        publishCatalog()
    }

    private fun publishCatalog() {
        screen.update {
            it.copy(
                loading = false, chat = workspace.chats.find { chat -> chat.summary.id == workspace.selectedId }
                    ?.summary,
                chats = matchingChats(it.chatSearch), navigation = sidebar(it.chatSearch))
        }
    }

    private suspend fun newChat(
        project: String = workspace.activeProjectId ?: screen.value.chat?.project ?: AuroraNavigation.Ungrouped,
        engineId: String? = null,
    ) {
        if (catalog != null) {
            val projectId = project.takeUnless { it == AuroraNavigation.Ungrouped }
            val directory = projectId?.let(catalog::directory)
            require(engineId == null || directory != null) { "Для работы с движком откройте папку проекта" }
            val opened = if (directory != null) {
                val sessions = requireNotNull(engineSessions)
                val owner = requireNotNull(engineId ?: sessions.defaultEngineId()) {
                    "Выберите движок для нового диалога"
                }
                sessions.open(requireNotNull(projectId), owner, null)
            } else null
            val id = opened?.localId ?: catalog.createLocal(projectId)
            engineSessions?.clearSelection()
            workspace = catalog.load(workspace).copy(
                collapsedNavigation = workspace.collapsedNavigation - (ProjectPrefix + project),
            )
            openChat(id)
            if (opened != null) attachSession(opened)
            return
        }
        val id = nextId()
        val entries = workspace.chats.toMutableList()
        val firstInProject = entries.indexOfFirst { it.summary.project == project }.coerceAtLeast(0)
        entries.add(firstInProject, AuroraChat(Chat(id, "Новый диалог", project)))
        workspace = workspace.copy(
            chats = entries, collapsedNavigation = workspace.collapsedNavigation - (ProjectPrefix + project),
        )
        openChat(id)
    }

    private fun readAt(action: AiStudioAction.ReadAt) {
        if (workspace.selectedId != action.chatId || selected.anchor == action.anchor) return
        updateChat(action.chatId) { it.copy(anchor = action.anchor) }
        saveRequests.trySend(Unit)
    }

    private fun showChats(query: String) {
        screen.update {
            it.copy(
                chatSearch = query, chats = matchingChats(query),
                navigation = sidebar(query), navigationFocusId = null,
            )
        }
    }

    private fun showChatSearch(visible: Boolean) {
        if (!visible) showChats("")
        screen.update { it.copy(chatSearchVisible = visible) }
    }

    private fun toggleNavigation(id: String) {
        val moreIndex = if (id.startsWith(MorePrefix)) screen.value.navigation.indexOfFirst { it.id == id } else -1
        workspace = if (id.startsWith(MorePrefix)) {
            if (moreIndex < 0) return
            workspace.copy(expandedProjects = workspace.expandedProjects + id.removePrefix(MorePrefix))
        } else {
            workspace.copy(collapsedNavigation = workspace.collapsedNavigation.toggle(id))
        }
        screen.update {
            val navigation = sidebar(it.chatSearch)
            // The first revealed chat takes the place of the More row.
            it.copy(navigation = navigation, navigationFocusId = navigation.getOrNull(moreIndex)?.id)
        }
        save()
    }

    private fun sidebar(query: String) = workspace.navigation(query, turns.mapValues { it.value.activity })

    private fun archiveChat(id: String) {
        if (id in workspace.archivedChats || workspace.chats.none { it.summary.id == id }) return
        catalog?.archive(id)
        workspace = workspace.copy(archivedChats = workspace.archivedChats + id)
        screen.update { current ->
            val removedIndex = current.navigation.indexOfFirst { it.id == id }
            val navigation = sidebar(current.chatSearch)
            val focus = if (removedIndex >= 0) {
                navigation.asSequence().drop(removedIndex).firstOrNull { it.kind != NavigationKind.More }
                    ?: navigation.lastOrNull { it.kind != NavigationKind.More }
            } else null
            current.copy(
                chats = matchingChats(current.chatSearch), navigation = navigation, navigationFocusId = focus?.id,
            )
        }
        save()
    }

    private fun matchingChats(query: String): List<Chat> = workspace.activeChats().filter {
        it.title.contains(query, ignoreCase = true) || it.project.contains(query, ignoreCase = true)
    }.toList()

    private fun subagents(chatId: String): List<AiStudioScreenState.Subagent> = workspace.chats.mapNotNull {
        val child = it.summary
        if (child.parentId == chatId && child.mockSubagent) {
            AiStudioScreenState.Subagent(child.id, child.title)
        } else null
    }

    private fun updateSummary(transform: (Chat) -> Chat) {
        updateChat(workspace.selectedId) { it.copy(summary = transform(it.summary)) }
        screen.update {
            it.copy(chat = selected.summary, chats = matchingChats(it.chatSearch), navigation = sidebar(it.chatSearch))
        }
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

    private suspend fun sendNative(draft: Draft) {
        val chat = selected
        check(chat.attachments.isEmpty()) { "Этот движок принимает только текстовые сообщения" }
        val text = draft.text.trim()
        if (text.isEmpty()) return
        if (selectedSession == null) selectChat(chat.summary.id)
        val session = requireNotNull(selectedSession)
        if (session.acceptsPrompt()) {
            session.dispatch(SessionEvent.SendPrompt(text))
            val title = if (chat.summary.title == "Новый диалог") text.lineSequence().first().take(MaxTitleLength)
                else chat.summary.title
            catalog?.recordActivity(chat.summary.id, title)
            editor.compareAndSet(draft, draft.copy(text = ""))
            save()
        }
    }

    private fun send(draft: Draft) {
        val chat = selected
        val chatId = chat.summary.id
        if (draft.chatId != chatId || turns.containsKey(chatId)) return
        val text = draft.text.trim()
        if (text.isEmpty() && chat.attachments.isEmpty()) return
        editor.compareAndSet(draft, draft.copy(text = ""))
        updateChat(chat.summary.id) { record ->
            record.copy(
                attachments = listOf(),
                summary = if (record.summary.title == "Новый диалог") {
                    val firstLine = text.lineSequence().firstOrNull().orEmpty().take(MaxTitleLength)
                    record.summary.copy(title = firstLine.ifBlank { record.attachments.first().name })
                } else {
                    record.summary
                },
            )
        }
        catalog?.recordActivity(chatId, selected.summary.title)
        val user = User(
            nextId(), MockTime, text, chat.attachments, Delivery.Sending,
            scenario = chat.summary.scenario, access = chat.summary.access,
        )
        newTurn(user).sendPrompt()
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

    private fun showFiles(query: String) {
        screen.update {
            if (it.filesVisible && it.fileSearchVisible) {
                it.copy(fileSearch = query, fileGroups = allFileGroups.filterFiles(query))
            } else {
                it
            }
        }
    }

    private fun showFileSearch(visible: Boolean) {
        screen.update {
            when {
                !it.filesVisible -> it
                visible -> it.copy(fileSearchVisible = true)
                else -> it.copy(fileSearchVisible = false, fileSearch = "", fileGroups = allFileGroups)
            }
        }
    }

    private fun refreshFiles(messages: List<Message>) {
        val next = files(messages)
        if (next == allFiles) return
        allFiles = next
        allFileGroups = prepareFileGroups(next)
    }

    private fun files(messages: List<Message>): List<File> = buildList {
        messages.forEach { message ->
            when (message) {
                is User -> addAll(message.files)
                is Reply -> message.result?.let(::add)
            }
        }
    }.distinctBy(File::id)

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
        if (chatId != workspace.selectedId && structural) {
            screen.update { it.copy(navigation = sidebar(it.chatSearch)) }
        }
        if (chatId == workspace.selectedId) {
            requestRender(turn)
            val messages = if (structural) history(selected) else screen.value.messages
            if (structural) refreshFiles(messages)
            screen.update {
                it.copy(
                    chat = selected.summary, chats = if (structural) matchingChats(it.chatSearch) else it.chats,
                    navigation = if (structural) sidebar(it.chatSearch) else it.navigation,
                    messages = messages, live = turn.reply, request = turn.request, activity = turn.activity,
                    fileGroups = if (structural) allFileGroups.filterFiles(it.fileSearch) else it.fileGroups,
                    attachments = selected.attachments,
                )
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
        val previous = requested
        if (previous?.turn !== turn || reply == null) stream.value = LiveText()
        if (turn == null || reply == null) {
            requested = null
            return
        }
        if (previous?.turn === turn && previous.text == reply.text) return
        val next = RenderRequest(turn, reply.id, reply.text)
        requested = next
        renderRequests.trySend(next)
    }

    internal fun finish(chatId: String, reply: Reply? = null) {
        if (reply != null) {
            val latest = stream.value
            if (chatId == workspace.selectedId && latest.replyId == reply.id && latest.text == reply.text) {
                reply.historyContent?.lastStreamDocument = latest.document
            }
            putMessage(chatId, reply)
        }
        turns.remove(chatId)
        updateChat(chatId) { it.copy(interrupted = null) }
        val messages = if (chatId == workspace.selectedId) history(selected) else null
        if (messages != null) {
            requestRender(null)
            refreshFiles(messages)
        }
        screen.update {
            val navigation = sidebar(it.chatSearch)
            val next = if (messages == null) {
                it.copy(navigation = navigation)
            } else {
                it.copy(
                    messages = messages, live = null, request = null, activity = Activity.Idle,
                    fileGroups = allFileGroups.filterFiles(it.fileSearch), navigation = navigation,
                )
            }
            if (reply?.status == ReplyStatus.Completed) next.collapseWork(reply.id) else next
        }
        save()
    }

    private fun AiStudioScreenState.collapseWork(replyId: String): AiStudioScreenState {
        val prefix = "$replyId/"
        return copy(
            expandedWork = expandedWork - replyId,
            expandedSteps = expandedSteps.filterNot { it.startsWith(prefix) }.toSet(),
            expandedEvents = expandedEvents.filterNot { it.startsWith(prefix) }.toSet(),
        )
    }

    internal fun event(command: () -> Unit) = enqueue { command() }

    private fun chat(id: String): AuroraChat = workspace.chats.first { it.summary.id == id }

    private fun updateChat(id: String, transform: (AuroraChat) -> AuroraChat) {
        workspace = workspace.copy(chats = workspace.chats.map { if (it.summary.id == id) transform(it) else it })
    }

    private fun history(chat: AuroraChat): List<Message> {
        val initial = fixture.getOrPut(chat.summary.id) { AuroraMock.history(chat.summary.id) }
        return if (chat.messages.isEmpty()) initial else chat.messages + initial
    }

    private fun rememberDraft(draft: Draft) {
        val id = draft.chatId ?: return
        if (chat(id).draft != draft.text) updateChat(id) { it.copy(draft = draft.text) }
    }

    private fun save() {
        rememberDraft(editor.value)
        val snapshot = if (catalog == null) workspace else workspace.copy(
            chats = workspace.chats.filter { it.summary.id in loadedChats },
        )
        snapshots.trySend(snapshot)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun restore(): AuroraWorkspace = try {
        storage.restore {
            screen.update { it.copy(storageError = "Не удалось перенести прежние диалоги") }
            snapshots.close()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // This workspace is temporary. Never replace a snapshot we could not read.
        snapshots.close()
        screen.update {
            it.copy(storageError = "Не удалось восстановить диалоги. Изменения в этом сеансе не сохраняются.")
        }
        if (catalog == null) AuroraMock.workspace() else AuroraWorkspace("", listOf())
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun persist(snapshot: AuroraWorkspace) {
        try {
            storage.save(snapshot)
            screen.update { if (it.storageError == SaveError) it.copy(storageError = null) else it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            screen.update { it.copy(storageError = SaveError) }
        }
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    private companion object {
        const val SaveError = "Не удалось сохранить диалог"
        const val MaxTitleLength = 48
        const val RenderInterval = 16L
        const val SaveInterval = 150L
        const val MockTime = "14:04"
    }
}
