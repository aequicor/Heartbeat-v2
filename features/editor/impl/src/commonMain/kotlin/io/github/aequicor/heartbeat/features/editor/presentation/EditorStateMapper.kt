package io.github.aequicor.heartbeat.features.editor.presentation

import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.domain.model.Note

/** Maps domain notes to UI state and back. */
internal fun Note.toEditorState(): EditorState =
    EditorState(title = title, content = content)

internal fun EditorState.toNote(id: String): Note =
    Note(id = id, title = title, content = content)
