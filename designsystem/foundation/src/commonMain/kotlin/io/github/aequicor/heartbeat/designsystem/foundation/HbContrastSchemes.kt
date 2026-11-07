package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * High-contrast mapping of the semantic palette (HB-DS-005, HB-DS-012).
 *
 * The mapping is deterministic instead of a second hand-picked palette: secondary text collapses
 * onto primary text, the subtle separator takes the required-control boundary, and that boundary
 * takes the secondary text color, which already reaches the text contrast requirement. Disabled
 * text keeps its role, because a disabled control must stay distinguishable from an enabled one.
 * The action pair, status pairs and selection foreground are already validated against their
 * containers and stay unchanged.
 */
public object HbContrastSchemes {

    public fun highContrast(base: HbColorScheme): HbColorScheme = base.copy(
        text = base.text.copy(secondary = base.text.primary),
        border = base.border.copy(
            subtle = base.border.control,
            control = base.text.secondary,
        ),
    )
}
