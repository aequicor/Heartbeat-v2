package io.github.aequicor.heartbeat.features.aiengine.api.session

public sealed interface CompactionStatus {
    public data object InProgress : CompactionStatus
    public data object Completed : CompactionStatus
    public data object Failed : CompactionStatus
    public data object Cancelled : CompactionStatus
    public data class Unknown(val code: String) : CompactionStatus
}
