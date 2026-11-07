package io.github.aequicor.heartbeat.designsystem.foundation

/** Semantic color roles of the supported appearances. */
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
            canvas = HbColor.hex(0xFFFFFF),
            base = HbColor.hex(0xFFFFFF),
            subtle = HbColor.hex(0xF4F4F5),
            raised = HbColor.hex(0xFFFFFF),
            input = HbColor.hex(0xFFFFFF),
        ),
        text = HbTextColors(
            primary = HbColor.hex(0x242426),
            secondary = HbColor.hex(0x66636C),
            disabled = HbColor.hex(0x98949F),
        ),
        border = HbBorderColors(
            subtle = HbColor.hex(0xE3E3E5),
            control = HbColor.hex(0x817B88),
        ),
        accent = HbAccentColors(
            brand = HbColor.hex(0x7B6D9A),
            foreground = HbColor.hex(0x6A587C),
            container = HbColor.hex(0xE9E5EF),
        ),
        action = HbActionColors(
            primaryBackground = HbColor.hex(0x353238),
            primaryForeground = HbColor.hex(0xFFFFFF),
            primaryHoverBackground = HbColor.hex(0x45404B),
            primaryPressedBackground = HbColor.hex(0x29252F),
        ),
        selection = HbSelectionColors(
            background = HbColor.hex(0xE9E5EF),
            foreground = HbColor.hex(0x6A587C),
            indicator = HbColor.hex(0x6A587C),
        ),
        status = HbStatusPalette(
            success = HbStatusColors(
                foreground = HbColor.hex(0x226A46),
                container = HbColor.hex(0xE9F6EE),
            ),
            warning = HbStatusColors(
                foreground = HbColor.hex(0x805500),
                container = HbColor.hex(0xFFF4DC),
            ),
            error = HbStatusColors(
                foreground = HbColor.hex(0xB42318),
                container = HbColor.hex(0xFFF0ED),
            ),
            information = HbStatusColors(
                foreground = HbColor.hex(0x245EA4),
                container = HbColor.hex(0xEDF4FF),
            ),
        ),
        focus = HbColor.hex(0x6A587C),
    )

    /**
     * Dark appearance. Surfaces stay opaque and neutral; the accent and action pairs are lightened
     * so the same roles keep their contrast on the dark backdrop.
     */
    public val Dark: HbColorScheme = HbColorScheme(
        surface = HbSurfaceColors(
            canvas = HbColor.hex(0x202022),
            base = HbColor.hex(0x242426),
            subtle = HbColor.hex(0x28282B),
            raised = HbColor.hex(0x303034),
            input = HbColor.hex(0x2A2A2D),
        ),
        text = HbTextColors(
            primary = HbColor.hex(0xEEEEF0),
            secondary = HbColor.hex(0xB8B4C0),
            disabled = HbColor.hex(0x8A8790),
        ),
        border = HbBorderColors(
            subtle = HbColor.hex(0x424246),
            control = HbColor.hex(0x87818F),
        ),
        accent = HbAccentColors(
            brand = HbColor.hex(0xBAAFD0),
            foreground = HbColor.hex(0xC5B8DC),
            container = HbColor.hex(0x3B3547),
        ),
        action = HbActionColors(
            primaryBackground = HbColor.hex(0xDCD4E8),
            primaryForeground = HbColor.hex(0x29252F),
            primaryHoverBackground = HbColor.hex(0xE8DFF4),
            primaryPressedBackground = HbColor.hex(0xC9BED8),
        ),
        selection = HbSelectionColors(
            background = HbColor.hex(0x3B3547),
            foreground = HbColor.hex(0xC5B8DC),
            indicator = HbColor.hex(0xC5B8DC),
        ),
        status = HbStatusPalette(
            success = HbStatusColors(
                foreground = HbColor.hex(0x9AD9B1),
                container = HbColor.hex(0x223B2C),
            ),
            warning = HbStatusColors(
                foreground = HbColor.hex(0xF5D18A),
                container = HbColor.hex(0x40351E),
            ),
            error = HbStatusColors(
                foreground = HbColor.hex(0xFFB4AB),
                container = HbColor.hex(0x482A2A),
            ),
            information = HbStatusColors(
                foreground = HbColor.hex(0xB0CFFF),
                container = HbColor.hex(0x25344B),
            ),
        ),
        focus = HbColor.hex(0xC5B8DC),
    )
}
