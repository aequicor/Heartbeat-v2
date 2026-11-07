package io.github.aequicor.heartbeat.designsystem.foundation

/** Background and container surfaces of the workspace. */
public data class HbSurfaceColors(
    public val canvas: HbColor,
    public val base: HbColor,
    public val subtle: HbColor,
    public val raised: HbColor,
    public val input: HbColor,
)

/** Text roles from primary content to unavailable text. */
public data class HbTextColors(
    public val primary: HbColor,
    public val secondary: HbColor,
    public val disabled: HbColor,
)

/** Separator and required-control boundaries. */
public data class HbBorderColors(
    public val subtle: HbColor,
    public val control: HbColor,
)

/** Lavender accent: brand mark, readable accent text and accent container. */
public data class HbAccentColors(
    public val brand: HbColor,
    public val foreground: HbColor,
    public val container: HbColor,
)

/** Dominant local action pair with its opaque resolved hover and pressed backgrounds. */
public data class HbActionColors(
    public val primaryBackground: HbColor,
    public val primaryForeground: HbColor,
    public val primaryHoverBackground: HbColor,
    public val primaryPressedBackground: HbColor,
)

/**
 * Selected row and text-selection roles. The background, foreground and indicator aliases keep
 * selection distinguishable from keyboard focus and from validation.
 */
public data class HbSelectionColors(
    public val background: HbColor,
    public val foreground: HbColor,
    public val indicator: HbColor,
)

/** Foreground and container of one status meaning. */
public data class HbStatusColors(
    public val foreground: HbColor,
    public val container: HbColor,
)

/** Status meanings; each pair is validated as a text and container combination. */
public data class HbStatusPalette(
    public val success: HbStatusColors,
    public val warning: HbStatusColors,
    public val error: HbStatusColors,
    public val information: HbStatusColors,
)

/**
 * Semantic color roles of one appearance. Reference palette values resolve into these roles; a
 * renderer adapter maps the roles to its own color type.
 */
public data class HbColorScheme(
    public val surface: HbSurfaceColors,
    public val text: HbTextColors,
    public val border: HbBorderColors,
    public val accent: HbAccentColors,
    public val action: HbActionColors,
    public val selection: HbSelectionColors,
    public val status: HbStatusPalette,
    public val focus: HbColor,
)
