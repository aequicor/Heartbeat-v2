package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Background and container surfaces of the workspace. */
@Immutable
data class HbSurfaceColors(
    val canvas: Color,
    val base: Color,
    val subtle: Color,
    val raised: Color,
    val input: Color,
)

/** Text roles from primary content to unavailable text. */
@Immutable
data class HbTextColors(
    val primary: Color,
    val secondary: Color,
    val disabled: Color,
)

/** Separator and required-control boundaries. */
@Immutable
data class HbBorderColors(
    val subtle: Color,
    val control: Color,
)

/** Lavender accent: brand mark, readable accent text and accent container. */
@Immutable
data class HbAccentColors(
    val brand: Color,
    val foreground: Color,
    val container: Color,
)

/** Dominant local action pair with its opaque resolved hover and pressed backgrounds. */
@Immutable
data class HbActionColors(
    val primaryBackground: Color,
    val primaryForeground: Color,
    val primaryHoverBackground: Color,
    val primaryPressedBackground: Color,
)

/** Selected row and text-selection roles. */
@Immutable
data class HbSelectionColors(
    val background: Color,
    val foreground: Color,
    val indicator: Color,
)

/** Foreground and container of one status meaning. */
@Immutable
data class HbStatusColors(
    val foreground: Color,
    val container: Color,
)

/** Status meanings; each pair is validated as a text and container combination. */
@Immutable
data class HbStatusPalette(
    val success: HbStatusColors,
    val warning: HbStatusColors,
    val error: HbStatusColors,
    val information: HbStatusColors,
)

/**
 * Semantic color roles of the resolved appearance, in Compose types. The values come from the
 * portable contract in `designsystem:foundation`; components read roles, not raw palette entries.
 */
@Immutable
data class HbColors(
    val surface: HbSurfaceColors,
    val text: HbTextColors,
    val border: HbBorderColors,
    val accent: HbAccentColors,
    val action: HbActionColors,
    val selection: HbSelectionColors,
    val status: HbStatusPalette,
    val focus: Color,
)
