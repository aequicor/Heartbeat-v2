package io.github.aequicor.heartbeat.features.aiengine.api

public sealed interface AiEngineCompactionStatus {
    public data object InProgress : AiEngineCompactionStatus
    public data object Completed : AiEngineCompactionStatus
    public data object Failed : AiEngineCompactionStatus
    public data object Cancelled : AiEngineCompactionStatus
    public data class Unknown(val code: String) : AiEngineCompactionStatus
}
