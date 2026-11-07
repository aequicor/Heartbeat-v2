package io.github.aequicor.heartbeat.designsystem.foundation

/** Appearance resolved by the theme: System follows the host where the host reports it. */
public enum class HbAppearance {
    System,
    Light,
    Dark,
}

/** Easing role of a transition; the curve itself is resolved by the renderer adapter. */
public enum class HbEasing {
    Enter,
    Exit,
    Standard,
}

/**
 * Motion vocabulary in milliseconds. Busy state takes effect immediately: only the indicator is
 * delayed by [busyIndicatorDelayMs].
 */
public data class HbMotion(
    public val smallStateChangeMs: Int,
    public val ordinaryTransitionMs: Int,
    public val deliberateTransitionMs: Int,
    public val busyIndicatorDelayMs: Int,
    public val copyFeedbackMs: Int,
    public val tooltipHoverDelayMs: Int,
)

/** Default motion timings. */
public object HbMotions {
    public val Default: HbMotion = HbMotion(
        smallStateChangeMs = 120,
        ordinaryTransitionMs = 220,
        deliberateTransitionMs = 360,
        busyIndicatorDelayMs = 400,
        copyFeedbackMs = 1800,
        tooltipHoverDelayMs = 600,
    )
}
