package io.github.aequicor.heartbeat.features.editor.api

/** One-shot events the editor emits. */
public sealed interface EditorEffect {
    public data object Saved : EditorEffect
}
