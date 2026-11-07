package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbGeometries

/**
 * Corner shapes of the application: control, field and floating-surface radii of the resolved
 * density. Components round by role, not by a per-component measurement.
 */
object HbShapes {
    private val geometry = HbGeometries.Dense
    val Small = RoundedCornerShape(geometry.controlRadius.dp)
    val Medium = RoundedCornerShape(geometry.fieldRadius.dp)
    val Large = RoundedCornerShape(geometry.floatingSurfaceRadius.dp)
}
