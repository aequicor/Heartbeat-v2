package io.github.aequicor.heartbeat.features.editor.component

import io.github.aequicor.heartbeat.features.editor.api.EditorStore

/**
 * Decompose adapter of the editor store. The component owns the store lifetime;
 * it does not duplicate state or business logic.
 */
interface EditorComponent {
    val store: EditorStore
}
