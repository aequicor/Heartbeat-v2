package io.github.aequicor.heartbeat.features.aistudio.domain.model

import io.github.aequicor.heartbeat.features.aiengine.api.session.ContextWindowUsage

internal data class ChatConversation(
    val configuration: ChatConfiguration?,
    val engineSessionId: String?,
    val contextStale: Boolean,
    val contextWindowUsage: ContextWindowUsage? = null,
)
