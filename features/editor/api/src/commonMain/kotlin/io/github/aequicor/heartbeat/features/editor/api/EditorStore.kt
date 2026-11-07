package io.github.aequicor.heartbeat.features.editor.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public contract of the editor feature. The store owns the UI state, handles
 * [EditorAction]s and emits one-shot [EditorEffect]s. Implementations live in
 * the feature `impl` module; UI and presentation adapters depend only on this
 * interface.
 *
 * Delivery contracts:
 * - [effects] are best-effort hints: the store does not wait for a collector and does not
 *   replay a missed effect, so a navigation decision must not depend on them. A critical
 *   result belongs to [EditorState] instead.
 * - [EditorState.saveError] is the durable result of the last failed [EditorAction.Save] and
 *   stays until the next save attempt, including while the user edits the note.
 */
public interface EditorStore {
    public val state: StateFlow<EditorState>
    public val effects: Flow<EditorEffect>

    public fun dispatch(action: EditorAction)
}

/**
 * Creates stores for a note. The [CoroutineScope] belongs to the caller — a
 * Decompose component or a BLoC — and defines the store lifetime.
 */
public interface EditorStoreFactory {
    public fun create(
        noteId: String,
        scope: CoroutineScope,
    ): EditorStore
}
