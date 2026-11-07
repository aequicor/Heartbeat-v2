package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbGeometries
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry

/**
 * Control sizes of the resolved density (HB-DS-009). A row uses a minimum height, so a large text
 * scale grows the row instead of clipping its label, and a touch host gets a 48-unit target without
 * a call-site change.
 */
object HbSizes {
    val controlHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalGeometry.current.controlHeight.dp

    val minimumTarget: Dp
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalGeometry.current.minimumTarget.dp

    val navigationRowHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalGeometry.current.navigationRowHeight.dp

    val settingsRowHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalGeometry.current.settingsRowHeight.dp

    /** Header height of a pane: the touch geometry uses the larger documented value. */
    val paneHeaderHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() = if (isTouch) {
            HbPaneGeometry.PaneHeaderTouch.dp
        } else {
            HbPaneGeometry.PaneHeaderDense.dp
        }

    /** Size of a standalone icon: the dense glyph stays compact, the touch glyph is larger. */
    val iconSize: Dp
        @Composable
        @ReadOnlyComposable
        get() = if (isTouch) TouchIconSize else DenseIconSize

    /** Size of the glyph inside a control, such as a chevron or a close mark. */
    val controlGlyph: Dp
        @Composable
        @ReadOnlyComposable
        get() = if (isTouch) TouchControlGlyph else DenseControlGlyph

    private val isTouch: Boolean
        @Composable
        @ReadOnlyComposable
        get() = HbThemeDefaults.LocalGeometry.current.controlHeight >= HbGeometries.Touch.controlHeight
}

private val DenseIconSize = 18.dp

private val TouchIconSize = 24.dp

private val DenseControlGlyph = 10.dp

private val TouchControlGlyph = 12.dp
