package io.github.aequicor.heartbeat.features.editor.presentation

import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorController
import io.github.aequicor.heartbeat.features.editor.api.EditorEffect
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.domain.usecase.LoadNote
import io.github.aequicor.heartbeat.features.editor.domain.usecase.SaveNote
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
        when (action) {
            is EditorAction.TitleChanged ->
                mutableState.update { it.copy(title = action.value) }

            is EditorAction.ContentChanged ->
                mutableState.update { it.copy(content = action.value) }

            EditorAction.Save -> save()
        }
    }

    private fun save() {
        if (mutableState.value.isSaving) {
            return
        }
        scope.launch {
            mutableState.update { it.copy(isSaving = true) }
            saveNote(mutableState.value.toNote(noteId))
            mutableState.update { it.copy(isSaving = false) }
            effectsChannel.send(EditorEffect.Saved)
        }
    }
}
