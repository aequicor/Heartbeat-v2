package io.github.aequicor.heartbeat.features.editor.api

/** UI state of the editor. */
public data class EditorState(
    val title: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
)
