package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * Contrast thresholds of the accessibility contract. Normal interface text keeps the 4.5:1 text
 * requirement instead of relying on the large-text exception, and a meaningful control boundary or
 * state indicator keeps 3:1 against its adjacent color.
 */
public object HbContrast {
    /** Minimum contrast ratio of normal interface text. */
    public const val TextMinimum: Double = 4.5

    /** Minimum contrast ratio of a meaningful control boundary or state indicator. */
    public const val NonTextMinimum: Double = 3.0
}
