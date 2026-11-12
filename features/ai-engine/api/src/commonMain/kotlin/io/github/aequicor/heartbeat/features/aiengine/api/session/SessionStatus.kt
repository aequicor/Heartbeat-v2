package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface SessionStatus {
    public data object Idle : SessionStatus
    public data object Running : SessionStatus
    public data object Unavailable : SessionStatus
    public data object Closed : SessionStatus
    public data class Failed(val reason: String) : SessionStatus

    public data class AwaitingPermission(
        val requests: List<PendingPermission>,
    ) : SessionStatus {
        init {
            require(requests.isNotEmpty()) { "At least one permission request is required" }
        }
    }
}
