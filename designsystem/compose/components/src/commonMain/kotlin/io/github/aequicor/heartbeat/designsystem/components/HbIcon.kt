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
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_arrow_up
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_brain
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_copy
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_file_text
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_folder_open
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_git_branch
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_pencil
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_rotate_ccw
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_settings
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_shield
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_shield_alert
import io.github.aequicor.heartbeat.designsystem.resources.ic_lucide_square_pen
import io.github.aequicor.heartbeat.designsystem.resources.ic_message_outline
import io.github.aequicor.heartbeat.designsystem.resources.ic_minus
import io.github.aequicor.heartbeat.designsystem.resources.ic_more
import io.github.aequicor.heartbeat.designsystem.resources.ic_plus
import io.github.aequicor.heartbeat.designsystem.resources.ic_search
import io.github.aequicor.heartbeat.designsystem.resources.ic_send
import io.github.aequicor.heartbeat.designsystem.resources.ic_stop
import io.github.aequicor.heartbeat.designsystem.resources.ic_terminal
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Icon of the shared vocabulary (HB-DS, icons).
 *
 * The identifier comes from the portable contract, and the component draws the bundled
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
        painter = painterResource(glyphAssets.getValue(glyph)),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** Central mapping from the semantic contract to the bundled outline assets. */
private val glyphAssets: Map<HbIconGlyph, DrawableResource> = mapOf(
    HbIconGlyph.ChevronDown to Res.drawable.ic_chevron_down,
    HbIconGlyph.Close to Res.drawable.ic_close,
    HbIconGlyph.Plus to Res.drawable.ic_plus,
    HbIconGlyph.Minus to Res.drawable.ic_minus,
    HbIconGlyph.Check to Res.drawable.ic_check,
    HbIconGlyph.Search to Res.drawable.ic_search,
    HbIconGlyph.Send to Res.drawable.ic_send,
    HbIconGlyph.Stop to Res.drawable.ic_stop,
    HbIconGlyph.More to Res.drawable.ic_more,
    HbIconGlyph.Eye to Res.drawable.ic_eye,
    HbIconGlyph.EyeOff to Res.drawable.ic_eye_off,
    HbIconGlyph.FolderOpen to Res.drawable.ic_lucide_folder_open,
    HbIconGlyph.Edit to Res.drawable.ic_lucide_square_pen,
    HbIconGlyph.Document to Res.drawable.ic_lucide_file_text,
    HbIconGlyph.Thinking to Res.drawable.ic_lucide_brain,
    HbIconGlyph.Branch to Res.drawable.ic_lucide_git_branch,
    HbIconGlyph.ArrowUp to Res.drawable.ic_lucide_arrow_up,
    HbIconGlyph.Settings to Res.drawable.ic_lucide_settings,
    HbIconGlyph.Copy to Res.drawable.ic_lucide_copy,
    HbIconGlyph.Pencil to Res.drawable.ic_lucide_pencil,
    HbIconGlyph.Retry to Res.drawable.ic_lucide_rotate_ccw,
    HbIconGlyph.Shield to Res.drawable.ic_lucide_shield,
    HbIconGlyph.ShieldAlert to Res.drawable.ic_lucide_shield_alert,
    HbIconGlyph.Message to Res.drawable.ic_message_outline,
    HbIconGlyph.Terminal to Res.drawable.ic_terminal,
)
