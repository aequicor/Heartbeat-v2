package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSurfaceColors
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

/**
 * Opaque surface container of a semantic role (HB-DS-002). Base surfaces carry no shadow: layering
 * is expressed by the role, and a shadow is added only by a floating surface such as a popup.
 */
@Composable
fun HbSurface(
    modifier: Modifier = Modifier,
    role: HbSurfaceRole = HbSurfaceRole.Base,
    shape: Shape = HbShapes.Large,
    contentPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = HbTheme.colors

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface.of(role))
            .padding(all = contentPadding),
        content = content,
    )
}

internal fun HbSurfaceColors.of(role: HbSurfaceRole): Color = when (role) {
    HbSurfaceRole.Canvas -> canvas
    HbSurfaceRole.Base -> base
    HbSurfaceRole.Subtle -> subtle
    HbSurfaceRole.Raised -> raised
    HbSurfaceRole.Input -> input
}
