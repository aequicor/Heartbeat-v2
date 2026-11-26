package io.github.aequicor.heartbeat.navigation.decompose

import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.navigation.api.AppRoute

/** A selectable entry in the window rail, supplied by the feature that owns its route. */
data class WorkspaceRegistration(
    val route: AppRoute,
    val label: String,
    val icon: HbIconGlyph,
    val priority: Int,
    val inFooter: Boolean = false,
)
