package io.github.aequicor.heartbeat.features.aiengine.api

public data class AiEnginePendingPermission(
    val requestId: String,
    val toolCallEntryId: String,
    val description: String,
    val options: List<AiEnginePermissionOption>,
)
