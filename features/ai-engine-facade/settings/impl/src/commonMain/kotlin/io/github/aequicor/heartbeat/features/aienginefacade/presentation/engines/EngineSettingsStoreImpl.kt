package io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines

import io.github.aequicor.heartbeat.features.aienginefacade.domain.exception.AcpConnectionCheckException
import io.github.aequicor.heartbeat.features.aienginefacade.domain.interactor.EngineSettingsInteractor
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.AcpConnectionSettings
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.ChatEngine
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenAction as Action
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState as State
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState.Editor
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsScreenState.Loaded
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions") // Keep serialized handlers and their state in the same Store.
internal class EngineSettingsStoreImpl(
    private val scope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher,
    private val interactor: EngineSettingsInteractor,
    private val ignoreEnvironmentNameCase: Boolean,
    private val close: () -> Unit,
) : EngineSettingsStore {

    private val mutableState = MutableStateFlow<State>(State.Loading)
    override val state = mutableState.asStateFlow()

    private val actions = Mutex()
    private val engines = mutableMapOf<String, ChatEngine>()
    private val connections = mutableMapOf<String, AcpConnectionSettings>()
    private var checkJob: Job? = null
    private var pendingAction: Action? = null

    @Throwable(CancellationException::class)
    override suspend fun dispatch(action: Action) {
        currentCoroutineContext().ensureActive()
        val operation = scope.async {
            if (action == Action.Check) checkConnection() else actions.withLock { handle(action) }
        }
        try {
            operation.await()
        } catch (cancelled: CancellationException) {
            operation.cancel(cancelled)
            throw cancelled
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun handle(action: Action) {
        val current = state.value as? Loaded
        when {
            action == Action.Load -> if (current == null) load()
            current == null -> if (action == Action.Close || action == Action.Back) close()
            current.dialog != null -> handleDialog(current, action)
            else -> handleLoaded(current, action)
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun handleDialog(current: Loaded, action: Action) {
        when (action) {
            Action.DismissDialog -> {
                pendingAction = null
                val field = current.requestedField.takeUnless { current.dialog == State.Dialog.Unsaved }
                publish(current.copy(dialog = null, requestedField = field))
            }
            Action.Discard -> if (current.dialog == State.Dialog.Unsaved) resumeNavigation(current, discard = true)
            Action.Save -> if (current.dialog == State.Dialog.Unsaved) save(current)
            is Action.ConfirmDelete -> {
                if ((current.dialog as? State.Dialog.Delete)?.id == action.id) delete(current, action.id)
            }
            else -> Unit
        }
    }

    @Suppress("CyclomaticComplexMethod") // One branch per action or group of navigation actions.
    @Throwable(CancellationException::class)
    private suspend fun handleLoaded(current: Loaded, action: Action) {
        when (action) {
            is Action.EditConnection -> editConnection(current, action.change)
            is Action.SetResponseFormat -> editEngine(current) { copy(responseFormat = action.format) }
            is Action.SetRules -> editEngine(current) { copy(rules = action.rules) }
            Action.Save -> save(current)
            is Action.StartRename -> if (action.id in engines) publish(current.copy(renameId = action.id))
            Action.CancelRename -> publish(current.copy(renameId = null))
            is Action.ConfirmRename -> rename(current, action)
            is Action.Delete -> {
                if (action.id == current.editor?.engineId) navigate(current, action)
                else requestDelete(current, action.id)
            }
            is Action.FindSetting -> find(current, action.field)
            Action.CancelFieldSearch -> publish(current.copy(requestedField = null))
            is Action.Select, Action.AddConnection, Action.OpenEngineSettings, Action.OpenConnection,
            Action.BackToList, Action.Back, Action.Close, is Action.Copy -> navigate(current, action)
            Action.Load, Action.Check, Action.Discard, Action.DismissDialog, is Action.ConfirmDelete -> Unit
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun load() {
        mutableState.value = State.Loading
        val start = time()
        withContext(defaultDispatcher) { interactor.load() }.fold(
            onSuccess = { catalog ->
                engines.clear()
                catalog.engines.forEach { engine -> engines[engine.id] = engine }
                connections.clear()
                connections.putAll(catalog.connections)
                publish(Loaded(rows()))
            },
            onFailure = {
                mutableState.value = State.Failed(
                    EngineOperationFailure(
                        "Не удалось загрузить список подключений",
                        "Логи загрузки подключений",
                        listOf(
                            info(start, "Начато чтение настроек из базы"),
                            error("Не удалось прочитать список подключений"),
                        ),
                    ),
                )
            },
        )
    }

    private fun publish(next: Loaded) {
        mutableState.value = next
    }

    private fun rows(): List<State.Row> = engines.values.sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
        .map { engine ->
            val command = connections.getValue(engine.connectionId).command
            val format = engine.localSettings["responseFormat"].orEmpty()
            val subtitle = if (engine.id != engine.connectionId && format.isNotBlank()) format else command
            State.Row(engine.id, engine.name, command, subtitle)
        }

    private fun select(
        current: Loaded,
        engine: ChatEngine,
        connectionPage: Boolean = engine.id == engine.connectionId,
        field: EngineSettingField? = null,
        rename: Boolean = false,
    ) {
        cancelCheck()
        val connection = connections.getValue(engine.connectionId)
        val editor = if (connectionPage) {
            Editor.Connection(engine.id, false, connection.form(ignoreEnvironmentNameCase))
        } else {
            engine.editor(connection)
        }
        publish(
            current.copy(
                rows = rows(),
                editor = editor,
                renameId = engine.id.takeIf { rename },
                requestedField = null,
                highlighted = field,
                highlightRequest = if (field == null) current.highlightRequest else current.highlightRequest + 1,
                notice = null,
                dialog = null,
            ),
        )
    }

    private fun editConnection(current: Loaded, change: AcpConnectionFormChange) {
        val editor = current.editor as? Editor.Connection ?: return
        val form = editor.form.edit(change, newConnection = editor.engineId == null)
        val connection = editor.engineId?.let { connections.getValue(engines.getValue(it).connectionId) }
        val baseline = connection?.form(ignoreEnvironmentNameCase) ?: emptyForm()
        val environment = form.comparableEnvironment()
        val changed = form.copy(environment = environment) !=
            baseline.copy(environment = baseline.comparableEnvironment())
        val launchChanged = form.command != editor.form.command || form.argumentText != editor.form.argumentText ||
            environment != editor.form.comparableEnvironment() || form.separator != editor.form.separator
        val check = if (launchChanged) {
            cancelCheck()
            when (editor.check) {
                is AcpCheckState.Failed, AcpCheckState.Retry -> AcpCheckState.Retry
                else -> AcpCheckState.Idle
            }
        } else editor.check
        publish(current.copy(editor = editor.copy(form = form, changed = changed, check = check), notice = null))
    }

    private fun editEngine(current: Loaded, edit: Editor.Engine.() -> Editor.Engine) {
        val next = (current.editor as? Editor.Engine)?.edit() ?: return
        val engine = engines.getValue(next.engineId)
        publish(current.copy(editor = next.copy(changed = engine != engine.withEdits(next)), notice = null))
    }

    @Throwable(CancellationException::class)
    private suspend fun navigate(current: Loaded, action: Action, confirmed: Boolean = false) {
        if (action is Action.Select && action.id == current.editor?.engineId && current.requestedField == null) return
        if (!confirmed && current.editor?.changed == true) {
            pendingAction = if (action == Action.Back) Action.BackToList else action
            publish(current.copy(dialog = State.Dialog.Unsaved, notice = null))
            return
        }
        performNavigation(current, action)
    }

    @Suppress("CyclomaticComplexMethod") // Each navigation action has a distinct destination or effect.
    @Throwable(CancellationException::class)
    private suspend fun performNavigation(current: Loaded, action: Action) {
        when (action) {
            is Action.Select -> chooseEngine(current, action.id)
            Action.AddConnection -> {
                cancelCheck()
                publish(current.copy(
                    editor = Editor.Connection(null, false, emptyForm()), renameId = null,
                    requestedField = null, highlighted = null, notice = null, dialog = null,
                ))
            }
            Action.OpenEngineSettings -> openEditor(current, connectionPage = false)
            Action.OpenConnection -> openEditor(current, connectionPage = true)
            is Action.FindSetting -> openEditor(current, action.field.connectionField, action.field)
            is Action.Copy -> copyEngine(current, action.id)
            is Action.Delete -> requestDelete(current, action.id)
            Action.BackToList, Action.Back -> backToList(current, closeFromList = action == Action.Back)
            Action.Close -> {
                cancelCheck()
                close()
            }
            else -> Unit
        }
    }

    private fun emptyForm(): AcpConnectionForm =
        AcpConnectionForm(ignoreEnvironmentNameCase = ignoreEnvironmentNameCase)

    private fun chooseEngine(current: Loaded, id: String) {
        val engine = engines[id] ?: return
        val field = current.requestedField
        val connectionPage = field?.connectionField ?: (engine.id == engine.connectionId)
        select(current, engine, connectionPage, field)
    }

    private fun openEditor(current: Loaded, connectionPage: Boolean, field: EngineSettingField? = null) {
        val id = current.editor?.engineId ?: return
        select(current, engines.getValue(id), connectionPage, field)
    }

    private fun backToList(current: Loaded, closeFromList: Boolean) {
        cancelCheck()
        if (closeFromList && current.editor == null) {
            close()
        } else {
            publish(
                current.copy(
                    editor = null, requestedField = null, highlighted = null, notice = null,
                    dialog = null, renameId = null,
                ),
            )
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun find(current: Loaded, field: EngineSettingField) {
        val connectionPage = field.connectionField
        val editor = current.editor
        if (editor == null || (editor.engineId == null && !connectionPage)) {
            if (current.rows.isNotEmpty()) {
                publish(current.copy(requestedField = field, renameId = null, notice = null))
            } else {
                val notice = if (editor == null) {
                    "Сначала добавьте подключение"
                } else {
                    "Сначала сохраните новое подключение"
                }
                publish(current.copy(requestedField = null, notice = notice))
            }
        } else if ((editor is Editor.Connection) == connectionPage) {
            publish(
                current.copy(
                    requestedField = null, highlighted = field, notice = null,
                    highlightRequest = current.highlightRequest + 1,
                ),
            )
        } else {
            navigate(current, Action.FindSetting(field))
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun save(current: Loaded) {
        val editor = current.editor?.takeIf { it.changed } ?: return
        val saved = when (editor) {
            is Editor.Connection -> saveConnection(current, editor)
            is Editor.Engine -> saveEngine(current, editor)
        } ?: return
        val next = current.copy(rows = rows(), editor = saved, notice = "Сохранено", dialog = null, saving = false)
        publish(next)
        resumeNavigation(next)
    }

    @Throwable(CancellationException::class)
    private suspend fun saveEngine(current: Loaded, editor: Editor.Engine): Editor.Engine? {
        val engine = engines.getValue(editor.engineId).withEdits(editor)
        if (!write(current) { interactor.save(engine) }) return null
        engines[engine.id] = engine
        return engine.editor(connections.getValue(engine.connectionId))
    }

    @Throwable(CancellationException::class)
    private suspend fun saveConnection(current: Loaded, editor: Editor.Connection): Editor.Connection? {
        if (!editor.form.canSave) return null
        val previous = editor.engineId?.let(engines::getValue)
        val id = previous?.connectionId ?: Uuid.random().toString()
        val connection = editor.form.settings(id)
        val saved = if (previous == null) {
            writeValue(current) { interactor.create(connection) }
        } else {
            previous.takeIf { write(current) { interactor.save(connection) } }
        }
        return saved?.let { engine ->
            engines[engine.id] = engine
            connections[id] = connection
            editor.copy(
                engineId = engine.id,
                form = connection.form(ignoreEnvironmentNameCase).copy(environment = editor.form.environment),
                changed = false,
            )
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun resumeNavigation(current: Loaded, discard: Boolean = false) {
        val next = pendingAction ?: return
        pendingAction = null
        val editor = current.editor
        val stable = if (discard && editor != null) {
            cancelCheck()
            val engine = editor.engineId?.let(engines::get)
            if (engine == null) current.copy(editor = null) else {
                val connection = connections.getValue(engine.connectionId)
                val saved = if (editor is Editor.Connection) {
                    Editor.Connection(engine.id, false, connection.form(ignoreEnvironmentNameCase))
                } else {
                    engine.editor(connection)
                }
                current.copy(editor = saved)
            }
        } else current
        val ready = stable.copy(dialog = null)
        publish(ready)
        navigate(ready, next, confirmed = true)
    }

    @Throwable(CancellationException::class)
    private suspend fun copyEngine(current: Loaded, id: String) {
        val copied = writeValue(current) { interactor.copy(id) } ?: return
        engines[copied.id] = copied
        select(current, copied, rename = true)
    }

    @Throwable(CancellationException::class)
    private suspend fun rename(current: Loaded, action: Action.ConfirmRename) {
        val previous = engines[action.id]?.takeIf { current.renameId == it.id && action.name.isNotBlank() } ?: return
        val engine = previous.copy(name = action.name.trim())
        if (!write(current) { interactor.rename(engine.id, engine.name) }) return
        engines[engine.id] = engine
        val editor = current.editor
        publish(current.copy(rows = rows(), renameId = null, editor =
            if (editor is Editor.Engine && editor.engineId == engine.id) editor.copy(name = engine.name) else editor,
            notice = null))
    }

    private fun requestDelete(current: Loaded, id: String) {
        val engine = engines[id] ?: return
        val name = connections.getValue(engine.connectionId).name
        publish(current.copy(dialog = State.Dialog.Delete(engine.id, engine.name, name), notice = null))
    }

    @Throwable(CancellationException::class)
    private suspend fun delete(current: Loaded, id: String) {
        if (!write(current) { interactor.delete(id) }) return
        engines.remove(id)
        val selected = current.editor?.engineId == id
        if (selected) cancelCheck()
        publish(current.copy(rows = rows(), editor = current.editor.takeUnless { selected }, dialog = null,
            renameId = null, notice = "Движок удалён"))
    }

    @Throwable(CancellationException::class)
    private suspend fun write(current: Loaded, call: suspend () -> Result<Unit>): Boolean =
        writeValue(current, call) != null

    @Throwable(CancellationException::class)
    private suspend fun <T : Any> writeValue(current: Loaded, call: suspend () -> Result<T>): T? {
        publish(current.copy(saving = true))
        try {
            return withContext(defaultDispatcher) { call() }.getOrElse {
                publish(current.copy(notice = "Не удалось сохранить изменения. Попробуйте ещё раз."))
                null
            }
        } finally {
            val latest = state.value as? Loaded
            if (latest?.saving == true) publish(latest.copy(saving = false))
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun checkConnection() {
        val job = currentCoroutineContext().job
        val settings = actions.withLock {
            val current = state.value as? Loaded ?: return
            val editor = (current.editor as? Editor.Connection)?.takeIf {
                it.form.canCheck && checkJob == null && current.dialog == null
            } ?: return
            checkJob = job
            publish(current.copy(editor = editor.copy(check = AcpCheckState.Checking), notice = null))
            editor.form.settings("")
        }
        val started = time()
        try {
            val result = withContext(defaultDispatcher) { interactor.check(settings) }
            val status = result.fold({ AcpCheckState.Connected(it) }, { checkFailure(started, it) })
            actions.withLock {
                if (checkJob === job) setCheck(status)
            }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                actions.withLock {
                    if (checkJob === job) {
                        checkJob = null
                        val editor = (state.value as? Loaded)?.editor as? Editor.Connection
                        if (editor?.check == AcpCheckState.Checking) setCheck(AcpCheckState.Idle)
                    }
                }
            }
        }
    }

    private fun setCheck(check: AcpCheckState) {
        val current = state.value as? Loaded ?: return
        val editor = current.editor as? Editor.Connection ?: return
        publish(current.copy(editor = editor.copy(check = check)))
    }

    private fun cancelCheck() {
        checkJob?.cancel()
        checkJob = null
    }

    private fun checkFailure(start: String, failure: kotlin.Throwable): AcpCheckState.Failed {
        val started = (failure as? AcpConnectionCheckException)?.started == true
        val message = if (started) {
            "Агент не завершил инициализацию ACP"
        } else {
            "Не удалось запустить программу. Проверьте команду или путь."
        }
        val entries = listOf(
            info(start, "Начата проверка ACP-подключения"),
            if (started) {
                info(start, "Программа запущена. Запрошен ACP initialize")
            } else {
                info(start, "Запрошен запуск программы")
            },
            if (started) {
                error("Не получен совместимый ответ ACP initialize")
            } else {
                error("Проверка завершилась до запуска агента")
            },
        )
        return AcpCheckState.Failed(
            EngineOperationFailure(message, "Логи проверки ACP-подключения", entries),
        )
    }

    private fun info(time: String, message: String) =
        EngineOperationFailure.Entry(time, EngineOperationFailure.Level.INFO, message)

    private fun error(message: String) =
        EngineOperationFailure.Entry(time(), EngineOperationFailure.Level.ERROR, message)

    private fun time(): String = Clock.System.now().toString().substringAfter('T').take(TimeOfDayLength) + " UTC"
}

private const val TimeOfDayLength = 8
