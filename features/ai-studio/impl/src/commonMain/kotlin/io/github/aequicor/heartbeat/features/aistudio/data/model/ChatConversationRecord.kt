package io.github.aequicor.heartbeat.features.aistudio.data.model

internal data class ChatConversationRecord(
    val engineId: String?,
    val model: String?,
    val reasoning: String?,
    val sessionId: String?,
    val stale: Long,
)
