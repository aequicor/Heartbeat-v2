package io.github.aequicor.heartbeat.features.editor.data.mapper

import io.github.aequicor.heartbeat.features.editor.data.datasource.NoteDto
import io.github.aequicor.heartbeat.features.editor.domain.model.Note

internal fun NoteDto.toNote(): Note = Note(id = id, title = title, content = content)

internal fun Note.toDto(): NoteDto = NoteDto(id = id, title = title, content = content)
