package io.github.aequicor.heartbeat.features.aiengine.api.session

public data class SessionTurn(val id: String, val status: Status) {

    public enum class Status { Running, Completed, Stopped, Failed }
}
