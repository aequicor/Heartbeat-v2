package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults

/**
 * Icon of the shared vocabulary (HB-DS, icons).
 *
 * The identifier comes from the portable contract, and the component draws it in the dense glyph
 * size by default. An icon-only action passes its action name to the surrounding control, so the
 * glyph never becomes the accessible name. [tint] carries the role color; the default is the
 * secondary text role.
 */
@Composable
fun HbIcon(
    glyph: HbIconGlyph,
    modifier: Modifier = Modifier,
    tint: Color = HbThemeDefaults.LocalColors.current.text.secondary,
    size: Dp = DenseGlyphSize,
) {
    Canvas(modifier = modifier.size(size)) {
        when (glyph) {
            HbIconGlyph.ChevronDown,
            HbIconGlyph.Close,
            HbIconGlyph.Plus,
            HbIconGlyph.Minus,
            HbIconGlyph.Check,
            -> drawStrokeGlyph(glyph = glyph, color = tint)

            HbIconGlyph.Search,
            HbIconGlyph.Send,
            HbIconGlyph.Stop,
            HbIconGlyph.More,
            HbIconGlyph.Eye,
            HbIconGlyph.EyeOff,
            -> drawShapeGlyph(glyph = glyph, color = tint)
        }
    }
}

private val DenseGlyphSize = 18.dp
