package io.github.aequicor.heartbeat.features.editor.domain.repository

import io.github.aequicor.heartbeat.features.editor.domain.model.Note

/** Contract of the note storage; data implementations live in `data/repository`. */
interface EditorRepository {
    suspend fun load(noteId: String): Note

    suspend fun save(note: Note)
}
