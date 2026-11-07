package io.github.aequicor.heartbeat.features.editor.data.datasource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.di.AppScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory note storage; replaces network or database data sources for now.
 * The instance is shared in [AppScope], so every access is guarded by [Mutex]
 * and the storage stays safe when stores run on different dispatchers.
 */
@SingleIn(AppScope::class)
class EditorDataSource @Inject constructor() {

    private val mutex = Mutex()
    private val notes = mutableMapOf<String, NoteDto>()

    suspend fun find(noteId: String): NoteDto? = mutex.withLock { notes[noteId] }

    suspend fun store(dto: NoteDto) {
        mutex.withLock { notes[dto.id] = dto }
    }
}
