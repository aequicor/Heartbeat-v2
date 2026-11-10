package io.github.aequicor.heartbeat.features.aiengine.api.session

public data class PendingPermission(
    val requestId: String,
    val toolCallEntryId: String,
    val description: String,
    val options: List<PermissionOption>,
)
