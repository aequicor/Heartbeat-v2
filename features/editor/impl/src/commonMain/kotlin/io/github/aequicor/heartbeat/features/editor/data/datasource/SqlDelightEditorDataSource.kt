package io.github.aequicor.heartbeat.features.editor.data.datasource

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.db.NoteQueries
import io.github.aequicor.heartbeat.features.editor.domain.model.Note

/**
 * SQLDelight implementation of [EditorDataSource].
 *
 * The queries use the shared application driver and the editor's own generated API; the class
 * knows neither the aggregate database nor the migrations of other features.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class SqlDelightEditorDataSource @Inject constructor(
    connection: DatabaseConnection,
) : EditorDataSource {

    private val queries = NoteQueries(connection.driver)

    override suspend fun find(noteId: String): Note? =
        queries.selectById(noteId) { id, title, content -> Note(id, title, content) }
            .executeAsOneOrNull()

    override suspend fun store(note: Note) {
        queries.upsert(note.id, note.title, note.content)
    }
}
