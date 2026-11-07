package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * Semantic color roles of the supported appearances.
 *
 * Every role resolves to a reference token of [HbReferenceColors]; no role repeats a channel
 * literal, so a change in the reference layer propagates through the semantic layer in one
 * direction (HB-DS-002). Components consume only the semantic roles.
 */
public object HbColorPalettes {

    /**
     * Light appearance.
     *
     * The neutral surfaces, text, accent and action values come from the legacy desktop palette.
     * The secondary text, control boundary, selection and status pairings are the 2.0 refinements;
     * each status text and container pair keeps a contrast ratio of at least 4.5:1.
     */
    public val Light: HbColorScheme = HbColorScheme(
        surface = HbSurfaceColors(
            canvas = HbReferenceColors.White,
            base = HbReferenceColors.White,
            subtle = HbReferenceColors.Paper,
            raised = HbReferenceColors.White,
            input = HbReferenceColors.White,
        ),
        text = HbTextColors(
            primary = HbReferenceColors.Ink,
            secondary = HbReferenceColors.InkSoft,
            disabled = HbReferenceColors.InkMuted,
        ),
        border = HbBorderColors(
            subtle = HbReferenceColors.Cloud,
            control = HbReferenceColors.Steel,
        ),
        accent = HbAccentColors(
            brand = HbReferenceColors.Violet,
            foreground = HbReferenceColors.VioletInk,
            container = HbReferenceColors.Mist,
        ),
        action = HbActionColors(
            primaryBackground = HbReferenceColors.GraphiteAction,
            primaryForeground = HbReferenceColors.White,
            primaryHoverBackground = HbReferenceColors.GraphiteActionHover,
            primaryPressedBackground = HbReferenceColors.GraphiteActionPressed,
        ),
        selection = HbSelectionColors(
            background = HbReferenceColors.Mist,
            foreground = HbReferenceColors.VioletInk,
            indicator = HbReferenceColors.VioletInk,
        ),
        status = HbStatusPalette(
            success = HbStatusColors(
                foreground = HbReferenceColors.Green,
                container = HbReferenceColors.GreenWash,
            ),
            warning = HbStatusColors(
                foreground = HbReferenceColors.Amber,
                container = HbReferenceColors.AmberWash,
            ),
            error = HbStatusColors(
                foreground = HbReferenceColors.Red,
                container = HbReferenceColors.Fog,
            ),
            information = HbStatusColors(
                foreground = HbReferenceColors.Blue,
                container = HbReferenceColors.BlueWash,
            ),
        ),
        focus = HbReferenceColors.VioletInk,
    )

    /**
     * Dark appearance. Surfaces stay opaque and neutral; the accent and action pairs are lightened
     * so the same roles keep their contrast on the dark backdrop.
     */
    public val Dark: HbColorScheme = HbColorScheme(
        surface = HbSurfaceColors(
            canvas = HbReferenceColors.Charcoal,
            base = HbReferenceColors.Graphite,
            subtle = HbReferenceColors.Slate,
            raised = HbReferenceColors.Basalt,
            input = HbReferenceColors.Coal,
        ),
        text = HbTextColors(
            primary = HbReferenceColors.Snow,
            secondary = HbReferenceColors.SnowSoft,
            disabled = HbReferenceColors.SnowMuted,
        ),
        border = HbBorderColors(
            subtle = HbReferenceColors.SlateLine,
            control = HbReferenceColors.SteelLine,
        ),
        accent = HbAccentColors(
            brand = HbReferenceColors.Lilac,
            foreground = HbReferenceColors.LilacInk,
            container = HbReferenceColors.Plum,
        ),
        action = HbActionColors(
            primaryBackground = HbReferenceColors.Pearl,
            primaryForeground = HbReferenceColors.GraphiteActionPressed,
            primaryHoverBackground = HbReferenceColors.PearlHover,
            primaryPressedBackground = HbReferenceColors.PearlPressed,
        ),
        selection = HbSelectionColors(
            background = HbReferenceColors.Plum,
            foreground = HbReferenceColors.LilacInk,
            indicator = HbReferenceColors.LilacInk,
        ),
        status = HbStatusPalette(
            success = HbStatusColors(
                foreground = HbReferenceColors.GreenLight,
                container = HbReferenceColors.GreenLightWash,
            ),
            warning = HbStatusColors(
                foreground = HbReferenceColors.AmberLight,
                container = HbReferenceColors.AmberLightWash,
            ),
            error = HbStatusColors(
                foreground = HbReferenceColors.RedLight,
                container = HbReferenceColors.RedLightWash,
            ),
            information = HbStatusColors(
                foreground = HbReferenceColors.BlueLight,
                container = HbReferenceColors.BlueLightWash,
            ),
        ),
        focus = HbReferenceColors.LilacInk,
    )
}
