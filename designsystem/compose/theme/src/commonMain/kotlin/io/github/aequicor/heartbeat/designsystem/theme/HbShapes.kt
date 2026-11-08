package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Corner shapes of the application, resolved from the density geometry: control, field and
 * floating-surface radii. Components round by role, not by a per-component measurement, so a touch
 * host gets the larger radii without changing a call site.
 */
object HbShapes {
    val Small: Shape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(HbThemeDefaults.LocalGeometry.current.controlRadius.dp)

    val Medium: Shape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(HbThemeDefaults.LocalGeometry.current.fieldRadius.dp)

    val Large: Shape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(HbThemeDefaults.LocalGeometry.current.floatingSurfaceRadius.dp)
}
