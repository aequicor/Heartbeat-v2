package io.github.aequicor.heartbeat.features.editor.data.datasource

/** Storage format of a note; an implementation detail, not a public contract. */
internal data class NoteDto(
    val id: String,
    val title: String,
    val content: String,
)
