package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.unit.dp

/**
 * Compose-level details shared by the Design System components. Color, opacity, spacing and type
 * values come from the portable tokens in `designsystem:foundation`; only the focus treatment stays
 * here because it is an interaction detail of the rendered kit.
 */
internal object HbComponentStates {

    /** Solid focus outline with a 2-unit width (HB-DS-012). */
    val focusedBorderWidth = 2.dp

    /** Gap between the control boundary and the focus outline (HB-DS-005). */
    val focusGap = 2.dp
}
