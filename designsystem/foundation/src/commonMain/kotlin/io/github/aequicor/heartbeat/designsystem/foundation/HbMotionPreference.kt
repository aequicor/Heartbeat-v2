package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * Motion preference of the user or the host (HB-DS-011).
 *
 * [System] follows the host accessibility setting where the host reports it; until the host
 * provides a value, the full motion vocabulary applies. [Reduced] removes translation, springing,
 * shimmer and attention pulses while keeping a static indication of activity, and it also shortens
 * the state transitions of the kit.
 */
public enum class HbMotionPreference {
    System,
    Reduced,
    Full,
    ;

    /**
     * Resolves [System] to the concrete preference reported by the host, so the theme never leaves
     * the `System` marker in a resolved value. A concrete value passes through unchanged; a host that
     * does not report a value defaults to the full vocabulary.
     */
    public fun resolve(system: HbMotionPreference): HbMotionPreference = when (this) {
        System -> when (system) {
            System -> Full
            Reduced, Full -> system
        }

        Reduced, Full -> this
    }
}
