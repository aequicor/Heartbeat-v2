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
        val fit = HbWorkspaceLayout.fit(availableWidth = 1038, secondPane = true)

        assertTrue(fit.secondPane)
        assertFalse(fit.inspector)
    }

    @Test
    fun sidebarCollapsesWhenEvenItDoesNotFit() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1000, sidebarWidth = 600)

        assertEquals(HbNavigationMode.Rail, fit.navigation)
        assertFalse(fit.sidebar)
        assertFalse(fit.inspector)
        assertFalse(fit.secondPane)
    }

    @Test
    fun wideWorkspaceKeepsEveryRequestedRegion() {
        val fit = HbWorkspaceLayout.fit(availableWidth = 1920, secondPane = true)

        assertTrue(fit.sidebar)
        assertTrue(fit.inspector)
        assertTrue(fit.secondPane)
    }
}
