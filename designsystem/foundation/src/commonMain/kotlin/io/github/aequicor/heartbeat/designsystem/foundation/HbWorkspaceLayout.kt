package io.github.aequicor.heartbeat.designsystem.foundation

/** Navigation presentation of the resolved workspace. */
public enum class HbNavigationMode {
    /** Compact navigation rail of the dense width. */
    Rail,

    /** Overlay drawer, because the workspace has a single focused pane. */
    Drawer,
}

/**
 * Regions that fit the available width (HB-DS-032). A region that does not fit is reported as
 * hidden, so the owner keeps the preferred arrangement in its workspace state and restores it when
 * the width returns.
 */
public data class HbWorkspaceFit(
    public val navigation: HbNavigationMode,
    public val sidebar: Boolean,
    public val inspector: Boolean,
    public val secondPane: Boolean,
)

/**
 * Fit policy of the workspace (HB-DS-031, HB-DS-032).
 *
 * The arrangement is computed from the usable width, never from a device category. When the
 * requested arrangement does not fit, a nonessential inspector is collapsed first, then the second
 * pane, then the sidebar; the navigation becomes a drawer only in the compact class. Minimum
 * content fit always wins over the width-class label.
 */
public object HbWorkspaceLayout {

    /**
     * Resolves the visible regions for [availableWidth].
     *
     * [sidebarWidth] and [inspectorWidth] are the user-chosen widths clamped to the documented
     * range by the caller; [secondPane] is the user preference for a second content pane; [sidebar]
     * and [inspector] are the requested visibility of those regions. A requested region stays
     * closed when the owner keeps it closed and is dropped only when the width cannot fit it.
     *
     * [splitterWidth] is the layout width of one pane separator, which the caller resolves from the
     * active density. It equals the resize hit target, not the painted line: the hit area is a real
     * layout region, so the panes' minimums do not silently overlap it.
     */
    public fun fit(
        availableWidth: Int,
        sidebarWidth: Int = HbPaneGeometry.SidebarPreferred,
        inspectorWidth: Int = HbPaneGeometry.InspectorPreferred,
        secondPane: Boolean = false,
        inspector: Boolean = true,
        sidebar: Boolean = true,
        splitterWidth: Int = HbPaneGeometry.SplitterLayout,
    ): HbWorkspaceFit {
        val compact = availableWidth <= HbLayoutBreakpoints.CompactMax
        val navigation = if (compact) HbNavigationMode.Drawer else HbNavigationMode.Rail
        if (compact) {
            return HbWorkspaceFit(
                navigation = navigation,
                sidebar = false,
                inspector = false,
                secondPane = false,
            )
        }

        val navigationWidth = HbPaneGeometry.NavigationRail
        var inspectorVisible = inspector
        var pane = secondPane
        var sidebarVisible = sidebar

        while (
            !fits(
                availableWidth,
                navigationWidth,
                sidebarWidth,
                inspectorWidth,
                inspectorVisible,
                pane,
                sidebarVisible,
                splitterWidth,
            )
        ) {
            when {
                inspectorVisible -> inspectorVisible = false
                pane -> pane = false
                sidebarVisible -> sidebarVisible = false
                else -> break
            }
        }

        val layoutFits = fits(
            availableWidth,
            navigationWidth,
            sidebarWidth,
            inspectorWidth,
            inspectorVisible,
            pane,
            sidebarVisible,
            splitterWidth,
        )
        return HbWorkspaceFit(
            navigation = navigation,
            sidebar = sidebarVisible && layoutFits,
            inspector = inspectorVisible && layoutFits,
            secondPane = pane && layoutFits,
        )
    }

    private fun fits(
        availableWidth: Int,
        navigationWidth: Int,
        sidebarWidth: Int,
        inspectorWidth: Int,
        inspector: Boolean,
        secondPane: Boolean,
        sidebar: Boolean,
        splitterWidth: Int,
    ): Boolean {
        var required = navigationWidth + HbPaneGeometry.SplitPaneMin
        if (sidebar) {
            required += sidebarWidth + splitterWidth
        }
        if (secondPane) {
            required += HbPaneGeometry.SplitPaneMin + splitterWidth
        }
        if (inspector) {
            required += inspectorWidth + splitterWidth
        }
        return required <= availableWidth
    }
}
