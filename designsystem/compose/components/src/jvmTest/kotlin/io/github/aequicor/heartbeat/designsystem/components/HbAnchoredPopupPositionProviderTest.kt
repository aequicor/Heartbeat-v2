package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Collision handling of the anchored popup (HB-DS-019): the list opens below its trigger, flips
 * above when the space below is smaller, and stays inside the window bounds.
 */
class HbAnchoredPopupPositionProviderTest {

    private val provider = HbAnchoredPopupPositionProvider(gap = GAP)

    @Test
    fun opensBelowWhenTheSpaceIsEnough() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(left = 10, top = 100, right = 210, bottom = 132),
            windowSize = IntSize(width = 800, height = 600),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(width = 200, height = 200),
        )

        assertEquals(IntOffset(x = 10, y = 136), position)
    }

    @Test
    fun flipsAboveWhenBelowIsTighterThanAbove() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(left = 10, top = 400, right = 210, bottom = 432),
            windowSize = IntSize(width = 800, height = 600),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(width = 200, height = 200),
        )

        assertEquals(IntOffset(x = 10, y = 196), position)
    }

    @Test
    fun keepsThePopupInsideTheWindow() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(left = 700, top = 100, right = 800, bottom = 132),
            windowSize = IntSize(width = 800, height = 600),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(width = 300, height = 200),
        )

        assertEquals(500, position.x)
        assertEquals(136, position.y)
    }

    @Test
    fun alignsThePopupToTheLogicalEndInRtl() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(left = 400, top = 100, right = 600, bottom = 132),
            windowSize = IntSize(width = 800, height = 600),
            layoutDirection = LayoutDirection.Rtl,
            popupContentSize = IntSize(width = 200, height = 200),
        )

        assertEquals(400, position.x)
        assertEquals(136, position.y)
    }

    @Test
    fun keepsTheRtlPopupInsideTheWindow() {
        val position = provider.calculatePosition(
            anchorBounds = IntRect(left = 50, top = 100, right = 250, bottom = 132),
            windowSize = IntSize(width = 400, height = 600),
            layoutDirection = LayoutDirection.Rtl,
            popupContentSize = IntSize(width = 300, height = 200),
        )

        assertEquals(0, position.x)
        assertEquals(136, position.y)
    }

    private companion object {
        private const val GAP = 4
    }
}
