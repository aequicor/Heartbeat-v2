package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * Contrast preference of the user or the host (HB-DS-005).
 *
 * [System] follows a forced-contrast setting where the host exposes it; until the host provides a
 * value, the standard palette applies. [High] strengthens secondary text and control boundaries
 * through the deterministic mapping of [HbContrastSchemes].
 */
public enum class HbContrastPreference {
    System,
    Standard,
    High,
    ;

    /**
     * Resolves [System] to the concrete preference reported by the host, so the theme never leaves
     * the `System` marker in a resolved value. A concrete value passes through unchanged; a host that
     * does not report a value defaults to the standard palette.
     */
    public fun resolve(system: HbContrastPreference): HbContrastPreference = when (this) {
        System -> when (system) {
            System -> Standard
            Standard, High -> system
        }

        Standard, High -> this
    }
}
