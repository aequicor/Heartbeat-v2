package io.github.aequicor.heartbeat.features.editor.component

import io.github.aequicor.heartbeat.features.editor.api.EditorController

/**
 * Decompose adapter of the editor controller. The component owns the
 * controller lifetime; it does not duplicate state or business logic.
 */
interface EditorComponent {
    val controller: EditorController
}
