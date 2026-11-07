package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.ic_check
import io.github.aequicor.heartbeat.designsystem.resources.ic_chevron_down
import io.github.aequicor.heartbeat.designsystem.resources.ic_close
import io.github.aequicor.heartbeat.designsystem.resources.ic_eye
import io.github.aequicor.heartbeat.designsystem.resources.ic_eye_off
import io.github.aequicor.heartbeat.designsystem.resources.ic_minus
import io.github.aequicor.heartbeat.designsystem.resources.ic_more
import io.github.aequicor.heartbeat.designsystem.resources.ic_plus
import io.github.aequicor.heartbeat.designsystem.resources.ic_search
import io.github.aequicor.heartbeat.designsystem.resources.ic_send
import io.github.aequicor.heartbeat.designsystem.resources.ic_stop
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Icon of the shared vocabulary (HB-DS, icons).
 *
 * The identifier comes from the portable contract, and the component draws the bundled Tabler Icons
 * outline asset of that identifier at the dense glyph size by default. The mapping between semantic
 * identifiers and assets lives only in [drawable]; UI components never reference an asset directly.
 * An icon-only action passes its action name to the surrounding control, so the glyph never becomes
 * the accessible name; [tint] carries the role color.
 */
@Composable
fun HbIcon(
    glyph: HbIconGlyph,
    modifier: Modifier = Modifier,
    tint: Color = HbTheme.colors.text.secondary,
    size: Dp = HbSizes.iconSize,
) {
    Image(
        painter = painterResource(glyph.drawable()),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Central mapping from the semantic contract to the bundled Tabler Icons asset. */
private fun HbIconGlyph.drawable(): DrawableResource = when (this) {
    HbIconGlyph.ChevronDown -> Res.drawable.ic_chevron_down
    HbIconGlyph.Close -> Res.drawable.ic_close
    HbIconGlyph.Plus -> Res.drawable.ic_plus
    HbIconGlyph.Minus -> Res.drawable.ic_minus
    HbIconGlyph.Check -> Res.drawable.ic_check
    HbIconGlyph.Search -> Res.drawable.ic_search
    HbIconGlyph.Send -> Res.drawable.ic_send
    HbIconGlyph.Stop -> Res.drawable.ic_stop
    HbIconGlyph.More -> Res.drawable.ic_more
    HbIconGlyph.Eye -> Res.drawable.ic_eye
    HbIconGlyph.EyeOff -> Res.drawable.ic_eye_off
}
