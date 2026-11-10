package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineSessionStatus {
    public data object Idle : AiEngineSessionStatus
    public data object Running : AiEngineSessionStatus
    public data object Unavailable : AiEngineSessionStatus
    public data object Closed : AiEngineSessionStatus
    public data class Failed(val reason: String) : AiEngineSessionStatus

    public data class AwaitingPermission(
        val requests: List<AiEnginePendingPermission>,
    ) : AiEngineSessionStatus {
        init {
            require(requests.isNotEmpty()) { "At least one permission request is required" }
        }
    }
}
