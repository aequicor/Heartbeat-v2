package io.github.aequicor.heartbeat.features.editor.api

/** One-shot events the editor emits. */
sealed interface EditorEffect {
    data object Saved : EditorEffect
}
