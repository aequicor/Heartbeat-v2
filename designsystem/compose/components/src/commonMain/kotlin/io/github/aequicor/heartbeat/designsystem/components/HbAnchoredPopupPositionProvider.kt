package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.max

/**
 * Places an anchored popup below its anchor and flips it above when the space below is smaller than
 * the popup and smaller than the space above (HB-DS-019, collision-aware). The popup is also kept
 * inside the window bounds, so an option list is never cut off by the edge of the workspace.
 */
internal class HbAnchoredPopupPositionProvider(
    private val gap: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val spaceBelow = windowSize.height - anchorBounds.bottom - gap
        val spaceAbove = anchorBounds.top - gap
        val placeBelow = popupContentSize.height <= spaceBelow || spaceBelow >= spaceAbove

        val y = if (placeBelow) {
            anchorBounds.bottom + gap
        } else {
            anchorBounds.top - popupContentSize.height - gap
        }
        val maxX = max(0, windowSize.width - popupContentSize.width)
        val maxY = max(0, windowSize.height - popupContentSize.height)

        return IntOffset(
            x = anchorBounds.left.coerceIn(0, maxX),
            y = y.coerceIn(0, maxY),
        )
    }
}
