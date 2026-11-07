package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * Layout classes of the workspace (HB-DS-031). The class is derived from the usable width after
 * system insets, never from a device category, and the minimum fit of the content always wins over
 * the class label.
 */
public enum class HbWindowWidth {
    /** Below 720: one focused pane, compact header, navigation in a drawer. */
    Compact,

    /** 720 to 1199: main pane plus a sidebar or rail when the minimum widths fit. */
    Medium,

    /** 1200 to 1599: sidebar and main pane with room for an inspector or a second pane. */
    Expanded,

    /** At least 1600: wider multi-pane workspace; reading width stays capped. */
    Wide;

    public companion object {
        /** Resolves the layout class of an available width in logical units. */
        public fun of(availableWidth: Int): HbWindowWidth = when {
            availableWidth <= HbLayoutBreakpoints.CompactMax -> Compact
            availableWidth <= HbLayoutBreakpoints.MediumMax -> Medium
            availableWidth <= HbLayoutBreakpoints.ExpandedMax -> Expanded
            else -> Wide
        }
    }
}

/** Width and height signals of the adaptive contract. */
public object HbLayoutBreakpoints {
    /** Largest width of the [HbWindowWidth.Compact] class. */
    public const val CompactMax: Int = 719

    /** Largest width of the [HbWindowWidth.Medium] class. */
    public const val MediumMax: Int = 1199

    /** Largest width of the [HbWindowWidth.Expanded] class. */
    public const val ExpandedMax: Int = 1599

    /** Height below which optional vertical chrome is reduced and pages replace tall dialogs. */
    public const val ShortHeight: Int = 640

    /** Local pane width below which secondary composer controls move to another row. */
    public const val NarrowPane: Int = 480
}

/**
 * Pane geometry of the workspace (HB-DS-032). A split arrangement requires the navigation, both
 * pane minimums, the inspector if present and the actual separators: two 360-unit conversations, a
 * 264-unit sidebar and two 1-unit separators need 986 usable units.
 */
public object HbPaneGeometry {
    public const val NavigationRail: Int = 52

    public const val SidebarMin: Int = 240

    public const val SidebarPreferred: Int = 264

    public const val SidebarMax: Int = 320

    public const val InspectorMin: Int = 280

    public const val InspectorPreferred: Int = 320

    public const val InspectorMax: Int = 420

    /** Minimum of one split conversation pane, including its own internal padding. */
    public const val SplitPaneMin: Int = 360

    public const val ReadingMax: Int = 760

    public const val SettingsFormMax: Int = 720

    public const val PaneHeaderDense: Int = 44

    public const val PaneHeaderTouch: Int = 56

    /** Width of one pane separator, counted twice in [TwoPaneWithSidebar]. */
    private const val SeparatorThickness: Int = 1

    /** Width required by the navigation rail, two split panes, a sidebar and two separators. */
    public const val TwoPaneWithSidebar: Int =
        NavigationRail + SidebarPreferred + 2 * SplitPaneMin + 2 * SeparatorThickness
}
