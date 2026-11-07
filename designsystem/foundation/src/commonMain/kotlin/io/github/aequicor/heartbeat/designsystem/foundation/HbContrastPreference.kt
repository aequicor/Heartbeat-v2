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
}
