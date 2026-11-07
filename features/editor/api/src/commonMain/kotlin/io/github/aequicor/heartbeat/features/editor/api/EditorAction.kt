package io.github.aequicor.heartbeat.features.editor.api

/** User intents the editor handles. */
sealed interface EditorAction {
    data class TitleChanged(val value: String) : EditorAction

    data class ContentChanged(val value: String) : EditorAction

    data object Save : EditorAction
}
