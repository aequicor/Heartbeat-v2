package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * Resolved density of a host. It is a stable host default plus an explicit user preference, not a
 * reaction to every pointer event.
 */
public enum class HbDensity {
    Dense,
    Comfortable,
    Touch,
}

/**
 * Control geometry of one density in logical units. [minimumTarget] is the effective hit area; a
 * smaller visual icon may sit inside it as long as adjacent targets do not overlap.
 */
public data class HbGeometry(
    public val controlHeight: Int,
    public val minimumTarget: Int,
    public val navigationRowHeight: Int,
    public val settingsRowHeight: Int,
    public val controlRadius: Int,
    public val fieldRadius: Int,
    public val floatingSurfaceRadius: Int,
)

/** Density defaults of the control geometry. */
public object HbGeometries {
    public val Dense: HbGeometry = HbGeometry(
        controlHeight = 32,
        minimumTarget = 32,
        navigationRowHeight = 32,
        settingsRowHeight = 44,
        controlRadius = 6,
        fieldRadius = 8,
        floatingSurfaceRadius = 12,
    )

    public val Comfortable: HbGeometry = HbGeometry(
        controlHeight = 40,
        minimumTarget = 40,
        navigationRowHeight = 40,
        settingsRowHeight = 48,
        controlRadius = 6,
        fieldRadius = 8,
        floatingSurfaceRadius = 12,
    )

    public val Touch: HbGeometry = HbGeometry(
        controlHeight = 48,
        minimumTarget = 48,
        navigationRowHeight = 48,
        settingsRowHeight = 56,
        controlRadius = 8,
        fieldRadius = 12,
        floatingSurfaceRadius = 16,
    )
}
