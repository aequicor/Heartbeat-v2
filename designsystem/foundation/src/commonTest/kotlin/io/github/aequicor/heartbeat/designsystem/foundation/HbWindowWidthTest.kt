package io.github.aequicor.heartbeat.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Boundary checks of the adaptive contract (HB-DS-031, HB-DS-032). The documented classes keep the
 * 720 and 1200 boundaries, so 719 is compact and 720 is medium, and 1600 is the first wide width.
 */
class HbWindowWidthTest {

    @Test
    fun compactEndsBeforeSevenHundredTwenty() {
        assertEquals(HbWindowWidth.Compact, HbWindowWidth.of(320))
        assertEquals(HbWindowWidth.Compact, HbWindowWidth.of(719))
    }

    @Test
    fun mediumStartsAtSevenHundredTwenty() {
        assertEquals(HbWindowWidth.Medium, HbWindowWidth.of(720))
        assertEquals(HbWindowWidth.Medium, HbWindowWidth.of(834))
        assertEquals(HbWindowWidth.Medium, HbWindowWidth.of(1199))
    }

    @Test
    fun expandedStartsAtTwelveHundred() {
        assertEquals(HbWindowWidth.Expanded, HbWindowWidth.of(1200))
        assertEquals(HbWindowWidth.Expanded, HbWindowWidth.of(1440))
        assertEquals(HbWindowWidth.Expanded, HbWindowWidth.of(1599))
    }

    @Test
    fun wideStartsAtSixteenHundred() {
        assertEquals(HbWindowWidth.Wide, HbWindowWidth.of(1600))
        assertEquals(HbWindowWidth.Wide, HbWindowWidth.of(1920))
    }

    @Test
    fun compactHeightEndsBeforeSixHundredForty() {
        assertEquals(HbWindowHeight.Compact, HbWindowHeight.of(0))
        assertEquals(HbWindowHeight.Compact, HbWindowHeight.of(639))
    }

    @Test
    fun regularHeightStartsAtSixHundredForty() {
        assertEquals(HbWindowHeight.Regular, HbWindowHeight.of(640))
        assertEquals(HbWindowHeight.Regular, HbWindowHeight.of(1080))
    }

    @Test
    fun twoPaneWithSidebarMatchesTheDocumentedSum() {
        assertEquals(
            52 + 264 + 2 * 360 + 2 * 32,
            HbPaneGeometry.TwoPaneWithSidebar,
        )
        assertEquals(1100, HbPaneGeometry.TwoPaneWithSidebar)
        assertEquals(32, HbPaneGeometry.SplitterLayout)
    }

    @Test
    fun paneMinimumsStayOrdered() {
        assertEquals(240, HbPaneGeometry.SidebarMin)
        assertEquals(320, HbPaneGeometry.SidebarMax)
        assertEquals(280, HbPaneGeometry.InspectorMin)
        assertEquals(420, HbPaneGeometry.InspectorMax)
        assertEquals(360, HbPaneGeometry.SplitPaneMin)
        assertEquals(760, HbPaneGeometry.ReadingMax)
    }
}
