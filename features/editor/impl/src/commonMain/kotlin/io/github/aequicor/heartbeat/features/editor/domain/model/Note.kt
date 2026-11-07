package io.github.aequicor.heartbeat.features.editor.domain.model

/** A note the editor works with. */
data class Note(
    val id: String,
    val title: String,
    val content: String,
)
