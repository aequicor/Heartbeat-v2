package io.github.aequicor.heartbeat.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fit policy checks (HB-DS-031, HB-DS-032): the collapse order is inspector, second pane, sidebar,
 * and the compact class never reserves a rail.
 */
class HbWorkspaceLayoutTest {

    @Test
    fun compactUsesDrawerAndOnePane() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 320, secondPane = true)

        assertEquals(HbNavigationMode.Drawer, fit.navigation)
        assertFalse(fit.sidebar)
        assertFalse(fit.inspector)
        assertFalse(fit.secondPane)
    }

    @Test
    fun mediumKeepsSidebarAndDropsInspector() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 720)

        assertEquals(HbNavigationMode.Rail, fit.navigation)
        assertTrue(fit.sidebar)
        assertFalse(fit.inspector)
        assertFalse(fit.secondPane)
    }

    @Test
    fun inspectorAppearsWhenTheSumFits() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1200)

        assertTrue(fit.sidebar)
        assertTrue(fit.inspector)
    }

    @Test
    fun secondPaneIsKeptAfterTheInspectorCollapses() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1200, secondPane = true)

        assertTrue(fit.secondPane)
        assertFalse(fit.inspector)
    }

    @Test
    fun secondPaneIsDroppedWhenTwoPanesAndTheSidebarDoNotFit() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 800, secondPane = true)

        assertTrue(fit.sidebar)
        assertFalse(fit.secondPane)
    }

    @Test
    fun inspectorCollapsesBeforeTheSecondPane() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1100, secondPane = true)

        assertTrue(fit.secondPane)
        assertFalse(fit.inspector)
    }

    @Test
    fun requestedClosedSidebarStaysClosedOnAWideScreen() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1920, sidebar = false)

        assertFalse(fit.sidebar)
        assertTrue(fit.inspector)
    }

    @Test
    fun requestedSidebarIsDroppedWhenTheSumDoesNotFit() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 800, sidebar = true, secondPane = true)

        assertTrue(fit.sidebar)
        assertFalse(fit.secondPane)
    }

    @Test
    fun splitterOccupiesItsHitTargetInTheFit() {
        val withoutSeparator = HbWorkspaceLayout.fit(
            availableWidth = 720,
            sidebarWidth = 300,
            splitterWidth = 0,
        )
        val withSeparator = HbWorkspaceLayout.fit(
            availableWidth = 720,
            sidebarWidth = 300,
            splitterWidth = HbPaneGeometry.SplitterLayout,
        )

        assertTrue(withoutSeparator.sidebar)
        assertFalse(withSeparator.sidebar)
    }

    @Test
    fun touchDensityPassesWiderSplittersToTheFit() {
        val dense = HbWorkspaceLayout.fit(availableWidth = 1100, secondPane = true, splitterWidth = 32)
        val touch = HbWorkspaceLayout.fit(availableWidth = 1100, secondPane = true, splitterWidth = 48)

        assertTrue(dense.secondPane)
        assertFalse(touch.secondPane)
    }

    @Test
    fun sidebarCollapsesWhenEvenItDoesNotFit() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 720, sidebarWidth = 600)

        assertEquals(HbNavigationMode.Rail, fit.navigation)
        assertFalse(fit.sidebar)
        assertFalse(fit.inspector)
        assertFalse(fit.secondPane)
    }

    @Test
    fun zeroWidthsAreClampedToTheDocumentedMinimums() {
        val fit = HbWorkspaceLayout.fit(
            availableWidth = 720,
            sidebarWidth = 0,
            inspectorWidth = 0,
        )

        assertTrue(fit.sidebar)
        assertFalse(fit.inspector, "The inspector minimum does not fit next to the sidebar")
    }

    @Test
    fun negativeWidthsAreClampedToTheDocumentedMinimums() {
        val fit = HbWorkspaceLayout.fit(
            availableWidth = 720,
            sidebarWidth = -100,
            inspectorWidth = -200,
        )

        assertTrue(fit.sidebar)
        assertFalse(fit.inspector, "Negative preferences must not pretend the inspector fits")
    }

    @Test
    fun oversizedWidthsAreClampedToTheDocumentedMaximums() {
        val fit = HbWorkspaceLayout.fit(
            availableWidth = 800,
            sidebarWidth = 10_000,
            inspectorWidth = 10_000,
        )

        assertTrue(fit.sidebar, "The sidebar preference is clamped to its maximum")
        assertFalse(fit.inspector)
    }

    @Test
    fun wideWorkspaceKeepsEveryRequestedRegion() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1920, secondPane = true)

        assertTrue(fit.sidebar)
        assertTrue(fit.inspector)
        assertTrue(fit.secondPane)
    }

    @Test
    fun requestedClosedInspectorStaysClosedOnAWideScreen() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1920, inspector = false)

        assertTrue(fit.sidebar)
        assertFalse(fit.inspector)
    }

    @Test
    fun requestedClosedInspectorIsNotReopenedWhenTheWidthReturns() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1600, secondPane = true, inspector = false)

        assertTrue(fit.sidebar)
        assertTrue(fit.secondPane)
        assertFalse(fit.inspector)
    }

    @Test
    fun requestedInspectorStillCollapsesWhenItDoesNotFit() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 720, inspector = true)

        assertTrue(fit.sidebar)
        assertFalse(fit.inspector)
    }
}
