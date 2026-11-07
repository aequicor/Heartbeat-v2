package io.github.aequicor.heartbeat.features.editor.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public contract of the editor feature. The controller owns the UI state,
 * handles [EditorAction]s and emits one-shot [EditorEffect]s. Implementations
 * live in the feature `impl` module; UI and presentation adapters depend only
 * on this interface.
 */
interface EditorController {
    val state: StateFlow<EditorState>
    val effects: Flow<EditorEffect>

    fun dispatch(action: EditorAction)
}

/**
 * Creates controllers for a note. The [CoroutineScope] belongs to the caller —
 * a Decompose component or a BLoC — and defines the controller lifetime.
 */
interface EditorControllerFactory {
    fun create(
        noteId: String,
        scope: CoroutineScope,
    ): EditorController
}
