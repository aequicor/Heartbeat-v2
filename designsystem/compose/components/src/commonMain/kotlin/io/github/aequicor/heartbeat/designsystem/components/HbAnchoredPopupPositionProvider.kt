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
    private val alignEnd: Boolean = false,
    private val pointerAnchor: IntOffset? = null,
    private val overlapAnchor: Boolean = false,
    private val badgeSize: Int? = null,
    private val onAnchorInPopup: (IntOffset) -> Unit = {},
    private val holdPosition: () -> Boolean = { false },
) : PopupPositionProvider {

    private var lastPosition: IntOffset? = null

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val previous = lastPosition
        if (previous != null && holdPosition()) return previous
        val anchor = when {
            pointerAnchor != null -> IntRect(anchorBounds.topLeft + pointerAnchor, IntSize.Zero)
            badgeSize != null -> {
                val width = minOf(anchorBounds.width, badgeSize)
                val height = minOf(anchorBounds.height, badgeSize)
                IntRect(anchorBounds.center - IntOffset(width / 2, height / 2), IntSize(width, height))
            }
            else -> anchorBounds
        }
        val below = if (overlapAnchor) anchor.top else anchor.bottom
        val above = if (overlapAnchor) anchor.bottom else anchor.top
        val spaceBelow = windowSize.height - below - gap
        val spaceAbove = above - gap
        val placeBelow = if (preferAbove) {
            popupContentSize.height > spaceAbove && spaceBelow >= spaceAbove
        } else {
            popupContentSize.height <= spaceBelow || spaceBelow >= spaceAbove
        }

        val y = if (placeBelow) {
            below + gap
        } else {
            above - popupContentSize.height - gap
        }
        val maxX = max(0, windowSize.width - popupContentSize.width)
        val maxY = max(0, windowSize.height - popupContentSize.height)
        val useRight = (layoutDirection == LayoutDirection.Rtl) != alignEnd
        val x = if (useRight) anchor.right - popupContentSize.width else anchor.left

        val position = IntOffset(
            x = x.coerceIn(0, maxX),
            y = y.coerceIn(0, maxY),
        )
        lastPosition = position
        onAnchorInPopup(anchor.center - position)
        return position
    }
}
