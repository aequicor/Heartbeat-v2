package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.max

/**
 * Places an anchored popup next to its anchor and keeps it inside the window bounds, so an option
 * list or a tooltip is never cut off by the edge of the workspace (HB-DS-019, collision-aware).
 *
 * A menu is placed below the anchor and flips above when the space below is smaller than the popup
 * and not larger than the space above. A tooltip ([preferAbove]) is placed above and flips below
 * only when the space above cannot hold it and the space below is larger, so a short window moves
 * the hint instead of clipping it.
 */
internal class HbAnchoredPopupPositionProvider(
    private val gap: Int,
    private val preferAbove: Boolean = false,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val spaceBelow = windowSize.height - anchorBounds.bottom - gap
        val spaceAbove = anchorBounds.top - gap
        val placeBelow = if (preferAbove) {
            popupContentSize.height > spaceAbove && spaceBelow >= spaceAbove
        } else {
            popupContentSize.height <= spaceBelow || spaceBelow >= spaceAbove
        }

        val y = if (placeBelow) {
            anchorBounds.bottom + gap
        } else {
            anchorBounds.top - popupContentSize.height - gap
        }
        val maxX = max(0, windowSize.width - popupContentSize.width)
        val maxY = max(0, windowSize.height - popupContentSize.height)
        val x = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.left.coerceIn(0, maxX)
            LayoutDirection.Rtl -> (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        }

        return IntOffset(
            x = x,
            y = y.coerceIn(0, maxY),
        )
    }
}
