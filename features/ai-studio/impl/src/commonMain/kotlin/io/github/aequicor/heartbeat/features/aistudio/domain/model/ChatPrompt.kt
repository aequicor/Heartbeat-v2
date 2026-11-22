package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal object ChatPrompt {

    fun title(text: String, firstAttachment: String? = null): String {
        val title = text.trim().lineSequence().first().take(48)
        return title.ifBlank { requireNotNull(firstAttachment) { "A message needs text or an attachment" } }
    }

    fun canSend(text: String, hasAttachments: Boolean, hasActiveTurn: Boolean): Boolean =
        !hasActiveTurn && (text.isNotBlank() || hasAttachments)
}
