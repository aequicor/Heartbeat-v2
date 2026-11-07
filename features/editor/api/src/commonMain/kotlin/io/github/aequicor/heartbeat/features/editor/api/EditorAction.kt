package io.github.aequicor.heartbeat.features.editor.api

/** User intents the editor handles. */
public sealed interface EditorAction {
    public data class TitleChanged(val value: String) : EditorAction

    public data class ContentChanged(val value: String) : EditorAction

    public data object Save : EditorAction

    /** Retries the note load after [EditorState.loadError]. */
    public data object Reload : EditorAction
}
