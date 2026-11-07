package io.github.aequicor.heartbeat.features.editor.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public contract of the editor feature. The store owns the UI state, handles
 * [EditorAction]s and emits one-shot [EditorEffect]s. Implementations live in
 * the feature `impl` module; UI and presentation adapters depend only on this
 * interface.
 */
interface EditorStore {
    val state: StateFlow<EditorState>
    val effects: Flow<EditorEffect>

    fun dispatch(action: EditorAction)
}

/**
 * Creates stores for a note. The [CoroutineScope] belongs to the caller — a
 * Decompose component or a BLoC — and defines the store lifetime.
 */
interface EditorStoreFactory {
    fun create(
        noteId: String,
        scope: CoroutineScope,
    ): EditorStore
}
