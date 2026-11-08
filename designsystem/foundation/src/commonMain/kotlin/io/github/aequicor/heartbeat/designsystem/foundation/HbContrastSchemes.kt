package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * High-contrast mapping of the semantic palette (HB-DS-005, HB-DS-012).
 *
 * The mapping is deterministic instead of a second hand-picked palette: secondary text collapses
 * onto primary text, the subtle separator takes the required-control boundary, and that boundary
 * takes the secondary text color, which already reaches the text contrast requirement. Disabled
 * text keeps its role, because a disabled control must stay distinguishable from an enabled one.
 * The action pair and status pairs are already validated against their containers and stay
 * unchanged.
 *
 * Selection is inverted: the selected container takes the primary text role and its content takes
 * the base surface role. A high-contrast theme must not rely on a low-contrast tint, so the
 * selected row reaches the text ratio against the adjacent surface while the ordinary palette keeps
 * a separate `selection.indicator` for the same purpose.
 */
public object HbContrastSchemes {

    public fun highContrast(base: HbColorScheme): HbColorScheme = base.copy(
        text = base.text.copy(secondary = base.text.primary),
        border = base.border.copy(
            subtle = base.border.control,
            control = base.text.secondary,
        ),
        selection = base.selection.copy(
            background = base.text.primary,
            foreground = base.surface.base,
            indicator = base.text.primary,
        ),
    )
}
