package io.github.aequicor.heartbeat.designsystem.patterns

/**
 * Visible state of the composer (HB-DS-020).
 *
 * The state is resolved by the feature store: the component renders it and never decides on its
 * own whether a message was accepted, queued or stopped. [Empty] and [Editing] describe the draft,
 * [Generating] and [Stopping] the run, and [Failed] and [Unavailable] keep their recoverable
 * content until the owner changes the state.
 */
enum class HbComposerState {
    Empty,
    Editing,
    PreparingAttachments,
    Submitting,
    Generating,
    Queued,
    WaitingForApproval,
    Stopping,
    Failed,
    Unavailable,
}
