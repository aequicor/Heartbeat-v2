package io.github.aequicor.heartbeat.designsystem.patterns

/** Availability of a configured connection (HB-DS-019). */
enum class HbConnectionStatus {
    Ready,
    Checking,
    Unavailable,
    Failed,
}
