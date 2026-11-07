package io.github.aequicor.heartbeat.features.editor.presentation

import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorController
import io.github.aequicor.heartbeat.features.editor.api.EditorEffect
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.domain.usecase.LoadNote
import io.github.aequicor.heartbeat.features.editor.domain.usecase.SaveNote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Universal editor controller: owns the state, runs use cases and emits
 * effects. It knows nothing about Decompose, Compose or BLoC — the caller
 * provides the [CoroutineScope] that defines its lifetime.
 *
 * Edits are ignored until the note is loaded, so the asynchronous load cannot
 * overwrite text the user already typed. A failed save sets [EditorState.saveError]
 * and always clears [EditorState.isSaving]; cancellation is not a save error.
 */
class DefaultEditorController(
    private val loadNote: LoadNote,
    private val saveNote: SaveNote,
    private val noteId: String,
    private val scope: CoroutineScope,
) : EditorController {

    private val mutableState = MutableStateFlow(EditorState())
    override val state: StateFlow<EditorState> = mutableState.asStateFlow()

    private val effectsChannel = Channel<EditorEffect>(Channel.BUFFERED)
    override val effects: Flow<EditorEffect> = effectsChannel.receiveAsFlow()

    init {
        scope.launch {
            mutableState.value = loadNote(noteId).toEditorState()
        }
    }

    override fun dispatch(action: EditorAction) {
        if (mutableState.value.isLoading) {
            return
        }
        when (action) {
            is EditorAction.TitleChanged ->
                mutableState.update { it.copy(title = action.value) }

            is EditorAction.ContentChanged ->
                mutableState.update { it.copy(content = action.value) }

            EditorAction.Save -> save()
        }
    }

    // The controller is the boundary that turns any persistence failure into UI state:
    // storage implementations may throw arbitrary exceptions, so the catch stays generic.
    @Suppress("TooGenericExceptionCaught")
    private fun save() {
        if (mutableState.value.isSaving) {
            return
        }
        // The flag is set synchronously so a second Save dispatched before the
        // coroutine starts cannot launch a parallel save.
        mutableState.update { it.copy(isSaving = true, saveError = null) }
        scope.launch {
            try {
                saveNote(mutableState.value.toNote(noteId))
                mutableState.update { it.copy(saveError = null) }
                effectsChannel.send(EditorEffect.Saved)
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
