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

    /** Separator counted once between two adjacent regions. */
    private const val SeparatorThickness: Int = 1

    /**
     * Resolves the visible regions for [availableWidth].
     *
     * [sidebarWidth] and [inspectorWidth] are the user-chosen widths clamped to the documented
     * range by the caller; [secondPane] is the user preference for a second content pane; [inspector]
     * is the requested visibility of the inspector, which stays closed when the owner keeps it
     * closed and is dropped only when the width cannot fit it.
     */
    public fun fit(
        availableWidth: Int,
        sidebarWidth: Int = HbPaneGeometry.SidebarPreferred,
        inspectorWidth: Int = HbPaneGeometry.InspectorPreferred,
        secondPane: Boolean = false,
        inspector: Boolean = true,
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
        var sidebar = true

        while (!fits(availableWidth, navigationWidth, sidebarWidth, inspectorWidth, inspectorVisible, pane, sidebar)) {
            when {
                inspectorVisible -> inspectorVisible = false
                pane -> pane = false
                sidebar -> sidebar = false
                else -> break
            }
        }

        val fit = fits(availableWidth, navigationWidth, sidebarWidth, inspectorWidth, inspectorVisible, pane, sidebar)
        return HbWorkspaceFit(
            navigation = navigation,
            sidebar = sidebar && fit,
            inspector = inspectorVisible && fit,
            secondPane = pane && fit,
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
    ): Boolean {
        var required = navigationWidth + HbPaneGeometry.SplitPaneMin
        var separators = 0
        if (sidebar) {
            required += sidebarWidth
            separators += SeparatorThickness
        }
        if (secondPane) {
            required += HbPaneGeometry.SplitPaneMin
            separators += SeparatorThickness
        }
        if (inspector) {
            required += inspectorWidth
            separators += SeparatorThickness
        }
        return required + separators <= availableWidth
    }
}
