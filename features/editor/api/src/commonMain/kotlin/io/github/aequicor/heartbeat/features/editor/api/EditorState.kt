package io.github.aequicor.heartbeat.features.editor.api

/** UI state of the editor. */
data class EditorState(
    val title: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveError: String? = null,
)
