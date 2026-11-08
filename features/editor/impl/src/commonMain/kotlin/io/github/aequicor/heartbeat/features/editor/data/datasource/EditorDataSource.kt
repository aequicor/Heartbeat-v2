package io.github.aequicor.heartbeat.features.editor.data.datasource

import io.github.aequicor.heartbeat.features.editor.domain.model.Note

/**
 * Storage boundary of the editor.
 *
 * The contract speaks domain models, so a feature implementation can swap the physical storage
 * without leaking SQLDelight types into the repository. [SqlDelightEditorDataSource] is the
 * production implementation; tests provide an in-memory one.
 */
public interface EditorDataSource {

    public suspend fun find(noteId: String): Note?

    public suspend fun store(note: Note)
}
