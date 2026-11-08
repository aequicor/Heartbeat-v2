package io.github.aequicor.heartbeat.features.editor.data.repository

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.data.datasource.EditorDataSource
import io.github.aequicor.heartbeat.features.editor.domain.model.Note
import io.github.aequicor.heartbeat.features.editor.domain.repository.EditorRepository

/** Repository implementation over the editor data source. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultEditorRepository(
    private val dataSource: EditorDataSource,
) : EditorRepository {

    override suspend fun load(noteId: String): Note =
        dataSource.find(noteId) ?: Note(id = noteId, title = "", content = "")

    override suspend fun save(note: Note) {
        dataSource.store(note)
    }
}
