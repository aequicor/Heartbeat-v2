package io.github.aequicor.heartbeat.features.editor.data.repository

import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.data.datasource.EditorDataSource
import io.github.aequicor.heartbeat.features.editor.data.mapper.toDto
import io.github.aequicor.heartbeat.features.editor.data.mapper.toNote
import io.github.aequicor.heartbeat.features.editor.domain.model.Note
import io.github.aequicor.heartbeat.features.editor.domain.repository.EditorRepository

/** In-memory implementation of [EditorRepository]. */
@ContributesBinding(AppScope::class)
class DefaultEditorRepository(
    private val dataSource: EditorDataSource,
) : EditorRepository {

    override suspend fun load(noteId: String): Note =
        dataSource.find(noteId)?.toNote() ?: Note(id = noteId, title = "", content = "")

    override suspend fun save(note: Note) {
        dataSource.store(note.toDto())
    }
}
