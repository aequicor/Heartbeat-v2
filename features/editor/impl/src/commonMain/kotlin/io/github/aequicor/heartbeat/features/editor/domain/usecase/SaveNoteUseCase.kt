package io.github.aequicor.heartbeat.features.editor.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.editor.domain.model.Note
import io.github.aequicor.heartbeat.features.editor.domain.repository.EditorRepository

/** Persists a note. */
class SaveNoteUseCase @Inject constructor(
    private val repository: EditorRepository,
) {

    suspend operator fun invoke(note: Note) {
        repository.save(note)
    }
}



