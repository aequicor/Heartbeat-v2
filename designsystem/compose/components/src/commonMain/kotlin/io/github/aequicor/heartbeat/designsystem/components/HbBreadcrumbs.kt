package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeTokens
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Path of the current location (HB-DS-019). Ancestors are actions that navigate, the last item is
 * the current location and is exposed as a heading; a long path scrolls instead of shrinking its
 * labels. [separator] is product copy and arrives from the owner.
 */
@Composable
fun HbBreadcrumbs(
    items: List<String>,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    separator: String = "/",
) {
    val colors = HbThemeTokens.colors

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
                BasicText(
                    text = item,
                    style = HbTypography.Label,
                    color = { colors.accent.foreground },
                    modifier = Modifier
                        .clip(HbShapes.Small)
                        .clickable(role = Role.Button) { onNavigate(index) }
                        .padding(vertical = HbSpacing.Xxs),
                )
            }
        }
    }
}
