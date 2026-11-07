package io.github.aequicor.heartbeat.features.editor.presentation

import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorEffect
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.api.EditorStore
import io.github.aequicor.heartbeat.features.editor.domain.usecase.LoadNoteUseCase
import io.github.aequicor.heartbeat.features.editor.domain.usecase.SaveNoteUseCase
import io.github.aequicor.heartbeat.features.editor.presentation.mapper.toEditorState
import io.github.aequicor.heartbeat.features.editor.presentation.mapper.toNote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Universal editor store: owns the state, runs use cases and emits effects. It
 * knows nothing about Decompose, Compose or BLoC — the caller provides the
 * [CoroutineScope] that defines its lifetime.
 *
 * The store keeps the editor usable under failures: a failed load sets
 * [EditorState.loadError] instead of blocking the store forever and can be
 * retried with [EditorAction.Reload]; a failed save sets [EditorState.saveError]
 * and always clears [EditorState.isSaving]. Edits are ignored while data is
 * loading or a save is running, so the async work cannot overwrite user input
 * and a save always persists the snapshot taken at the moment of the action.
 * Every decision and the state write it leads to happen in one atomic
 * compare-and-set ([transition]), so a concurrent action cannot change the
 * state between the check and the write. A destroyed store is closed: once the
 * owner [scope] is no longer active, [dispatch] ignores every action instead of
 * claiming a save that could never run. Cancellation is not a failure.
 *
 * The implementation is internal: the feature exposes [EditorStore], and the
 * store factory creates the instance inside the module.
 */
internal class DefaultEditorStore(
    private val loadNote: LoadNoteUseCase,
    private val saveNote: SaveNoteUseCase,
    private val noteId: String,
    private val scope: CoroutineScope,
) : EditorStore {

    private val mutableState = MutableStateFlow(EditorState())
    override val state: StateFlow<EditorState> = mutableState.asStateFlow()

    /**
     * Effects are one-shot hints for the UI: a save never waits for a collector and never
     * keeps [EditorState.isSaving] set because nobody listens. A slow collector loses the
     * oldest hint instead of suspending the store.
     */
    private val mutableEffects = MutableSharedFlow<EditorEffect>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val effects: Flow<EditorEffect> = mutableEffects.asSharedFlow()

    init {
        // EditorState starts in the loading state, so the constructor only launches the load.
        startLoad()
    }

    override fun dispatch(action: EditorAction) {
        when (action) {
            is EditorAction.TitleChanged -> edit { it.copy(title = action.value) }
            is EditorAction.ContentChanged -> edit { it.copy(content = action.value) }
            EditorAction.Save -> save()
            EditorAction.Reload -> reload()
        }
    }

    /**
     * Admits an edit only while the editor is idle. The check and the write run in one
     * [transition], so an edit that races with [save] is rejected instead of changing the state
     * the save already snapshotted.
     */
    private fun edit(transform: (EditorState) -> EditorState) {
        transition { state -> if (state.acceptsEdits) transform(state) else null }
    }

    /**
     * Reload exists to retry a failed load ([EditorState.loadError]), so it runs only when no
     * operation is active. A reload during a save would replace the state the save is working on.
     */
    private fun reload() {
        transition { state ->
            when {
                state.isLoading || state.isSaving || state.loadError == null -> null
                else -> state.copy(isLoading = true, loadError = null)
            }
        } ?: return
        startLoad()
    }

    /**
     * Applies [transform] atomically and returns the state it replaced, or `null` when the store
     * is closed or [transform] rejected the transition by returning `null`. A rejected transition
     * does not write the state, so it cannot be re-evaluated later, when the blocking condition
     * has cleared; an accepted transition re-runs [transform] on the latest state when another
     * action wins the race, so the decision and the write always concern the same state.
     */
    private fun transition(transform: (EditorState) -> EditorState?): EditorState? =
        // The owner scope defines the store lifetime: after it is destroyed, a new action must not
        // claim a state that only a coroutine could resolve.
        if (scope.isActive) transitionWhileActive(transform) else null

    private fun transitionWhileActive(transform: (EditorState) -> EditorState?): EditorState? {
        while (true) {
            val current = mutableState.value
            val next = transform(current) ?: return null
            if (mutableState.compareAndSet(current, next)) {
                return current
            }
        }
    }

    // Loading is the second boundary that turns an arbitrary storage failure into UI state.
    // The loading flag is claimed before the launch: by the initial state or by reload().
    // ATOMIC guarantees that the body runs even if the scope is cancelled between the claim and
    // the launch, so the failure path still clears the flag.
    @Suppress("TooGenericExceptionCaught")
    private fun startLoad() {
        scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                mutableState.value = loadNote(noteId).toEditorState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(isLoading = false, loadError = error.message ?: "Load failed")
                }
            }
        }
    }

    // The store is the boundary that turns any persistence failure into UI state:
    // storage implementations may throw arbitrary exceptions, so the catch stays generic.
    @Suppress("TooGenericExceptionCaught")
    private fun save() {
        // The snapshot and the running flag are claimed in one atomic transition: two concurrent
        // Save actions cannot both observe `isSaving == false`, and an edit cannot land between
        // the check and the claim, so the persisted note always matches the state the user saw.
        val snapshot = transition { state ->
            if (state.acceptsEdits) {
                state.copy(isSaving = true, saveError = null)
            } else {
                null
            }
        } ?: return
        // ATOMIC closes the window between the claim and the launch: if the scope is cancelled
        // right now, the body still runs to the first suspension and `finally` clears isSaving.
        scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                saveNote(snapshot.toNote(noteId))
                mutableState.update { it.copy(saveError = null) }
                mutableEffects.tryEmit(EditorEffect.Saved)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.update { it.copy(saveError = error.message ?: "Save failed") }
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }
}

/** Edits are admitted only while no load or save can overwrite them. */
private val EditorState.acceptsEdits: Boolean
    get() = !isLoading && !isSaving && loadError == null
