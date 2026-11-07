package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.unit.dp

/**
 * Visual states shared by the Design System components, so the default, disabled, focused and
 * error states are defined once instead of appearing inline in every component. The values are
 * deliberately local to the components: the full token system stays a separate concern, and
 * [io.github.aequicor.heartbeat.designsystem.theme.HbColors] remains the only palette source.
 */
internal object HbComponentStates {

    /** Content of an inactive component: the palette color, dimmed instead of replaced. */
    const val DISABLED_CONTENT_ALPHA = 0.38f

    /** Container of an inactive button, visible on the surface but clearly muted. */
    const val DISABLED_CONTAINER_ALPHA = 0.12f

    /** Border of a focused or invalid field and of a focused button. */
    val focusedBorderWidth = 2.dp

    /** Content overlay of a pressed button: lightens the container without a second palette. */
    const val PRESSED_OVERLAY_ALPHA = 0.12f
}
