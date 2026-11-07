package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * Opacity and effect tokens. Base surfaces and foreground roles stay opaque; a state layer or a
 * scrim is composited against the actual backdrop, and contrast is checked on the result.
 */
public object HbEffects {
    /** Neutral hover layer over a surface, equivalent to 5% of the foreground color. */
    public const val HoverLayer: Float = 0.05f

    /** Neutral pressed layer over a surface, equivalent to 9% of the foreground color. */
    public const val PressedLayer: Float = 0.09f

    /** Content of an inactive component, dimmed instead of replaced. */
    public const val DisabledContent: Float = 0.38f

    /** Container of an inactive component. */
    public const val DisabledContainer: Float = 0.12f

    /** Scrim behind an application modal in the light appearance. */
    public const val ScrimLight: Float = 0.32f

    /** Scrim behind an application modal in the dark appearance. */
    public const val ScrimDark: Float = 0.56f

    /** Popup shadow opacity in the light appearance. */
    public const val PopupShadowLight: Float = 0.12f

    /** Popup shadow opacity in the dark appearance. */
    public const val PopupShadowDark: Float = 0.40f

    /** Popup shadow blur radius in logical units; base surfaces have no shadow. */
    public const val PopupShadowBlur: Int = 12

    /** Popup shadow vertical offset in logical units. */
    public const val PopupShadowOffset: Int = 4
}
