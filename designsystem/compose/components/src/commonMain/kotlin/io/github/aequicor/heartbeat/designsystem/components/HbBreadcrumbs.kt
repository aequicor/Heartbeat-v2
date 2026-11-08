package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Path of the current location (HB-DS-019). Ancestors are actions that navigate, the last item is
 * the current location and is exposed as a heading; a long path scrolls instead of shrinking its
 * labels. Each navigable ancestor draws the shared focus outline and keeps the density minimum
 * target. [separator] is product copy and arrives from the owner.
 */
@Composable
fun HbBreadcrumbs(
    items: List<String>,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    separator: String = "/",
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                BasicText(
                    text = separator,
                    style = HbTypography.Caption,
                    color = { colors.text.secondary },
                )
            }
            if (index == items.lastIndex) {
                BasicText(
                    text = item,
                    style = HbTypography.Label,
                    color = { colors.text.primary },
                    modifier = Modifier.semantics { heading() },
                )
            } else {
                BreadcrumbLink(
                    label = item,
                    onClick = { onNavigate(index) },
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbLink(
    label: String,
    onClick: () -> Unit,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(HbShapes.Small)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .heightIn(min = HbSizes.minimumTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { colors.accent.foreground },
        )
    }
}
