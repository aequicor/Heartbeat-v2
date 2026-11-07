package io.github.aequicor.heartbeat.features.editor.data.datasource

import dev.zacsweers.metro.Inject

/** In-memory note storage; replaces network or database data sources for now. */
class EditorDataSource @Inject constructor() {

    private val notes = mutableMapOf<String, NoteDto>()

    fun find(noteId: String): NoteDto? = notes[noteId]

    fun store(dto: NoteDto) {
        notes[dto.id] = dto
    }
}
