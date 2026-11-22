package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ChatConversation(
    val configuration: ChatConfiguration?,
    val engineSessionId: String?,
    val contextStale: Boolean,
)
