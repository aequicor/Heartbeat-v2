package io.github.aequicor.heartbeat.designsystem.patterns

/** Visible state of one tool call (HB-DS-026). */
enum class HbToolStatus {
    Running,
    Succeeded,
    Failed,
    Unknown,
}
