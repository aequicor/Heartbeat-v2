package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal enum class SessionWorkState {
    Idle, Waiting, Working, Demand;

    val isActive: Boolean get() = this == Working || this == Demand
}
