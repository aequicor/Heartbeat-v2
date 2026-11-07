package io.github.aequicor.heartbeat.features.editor.presentation

import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorEffect
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.api.EditorStore
import io.github.aequicor.heartbeat.features.editor.domain.usecase.LoadNoteUseCase
import io.github.aequicor.heartbeat.features.editor.domain.usecase.SaveNoteUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * Cancellation is not a failure.
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
        load()
    }

    override fun dispatch(action: EditorAction) {
        when (action) {
            EditorAction.Reload -> reload()
            else -> dispatchEdit(action)
        }
    }

    /**
     * Reload exists to retry a failed load ([EditorState.loadError]), so it runs only when no
     * operation is active. A reload during a save would replace the state the save is working on.
     */
    private fun reload() {
        val current = mutableState.value
        if (current.isLoading || current.isSaving || current.loadError == null) {
            return
        }
        load()
    }

    private fun dispatchEdit(action: EditorAction) {
        val current = mutableState.value
        if (current.isLoading || current.isSaving || current.loadError != null) {
            return
        }
        when (action) {
            is EditorAction.TitleChanged ->
                mutableState.update { it.copy(title = action.value) }

            is EditorAction.ContentChanged ->
                mutableState.update { it.copy(content = action.value) }

            EditorAction.Save -> save()

            EditorAction.Reload -> Unit
        }
    }

    // Loading is the second boundary that turns an arbitrary storage failure into UI state.
    @Suppress("TooGenericExceptionCaught")
    private fun load() {
        mutableState.update { it.copy(isLoading = true, loadError = null) }
        scope.launch {
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
        // The snapshot is taken and the running flag is set in one atomic
        // compare-and-set: two concurrent Save actions cannot both observe
        // `isSaving == false` and start two saves, because only the first
        // compare-and-set succeeds. The snapshot is synchronous, so edits
        // dispatched while the save is running are ignored and the persisted
        // note always matches what the user saw.
        val snapshot = mutableState.value
        if (snapshot.isLoading || snapshot.isSaving || snapshot.loadError != null) {
            return
        }
        if (!mutableState.compareAndSet(snapshot, snapshot.copy(isSaving = true, saveError = null))) {
            return
        }
        scope.launch {
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
