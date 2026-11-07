package io.github.aequicor.heartbeat.features.editor.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.editor.domain.model.Note
import io.github.aequicor.heartbeat.features.editor.domain.repository.EditorRepository

/** Loads a note; a missing note yields an empty one with the requested id. */
class LoadNoteUseCase @Inject constructor(
    private val repository: EditorRepository,
) {

    suspend operator fun invoke(noteId: String): Note = repository.load(noteId)
}



