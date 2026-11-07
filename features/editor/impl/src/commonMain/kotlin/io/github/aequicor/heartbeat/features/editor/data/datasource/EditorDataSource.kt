package io.github.aequicor.heartbeat.features.editor.data.datasource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppScope

/** In-memory note storage; replaces network or database data sources for now. */
@SingleIn(AppScope::class)
class EditorDataSource @Inject constructor() {

    private val notes = mutableMapOf<String, NoteDto>()

    fun find(noteId: String): NoteDto? = notes[noteId]

    fun store(dto: NoteDto) {
        notes[dto.id] = dto
    }
}
