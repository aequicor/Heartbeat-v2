package io.github.aequicor.heartbeat.designsystem.patterns

/** Resolution state of an approval request (HB-DS-030). */
enum class HbApprovalState {
    Pending,
    Allowed,
    Rejected,
    Expired,
}
