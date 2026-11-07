package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeTokens
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Expandable row of a tree (HB-DS-019).
 *
 * The row exposes the expanded state through `expand` and `collapse` actions, and [depth] indents
 * the label without encoding the hierarchy in color. [stateDescription] carries the localized state
 * name. A tree is used only where the hierarchy helps; a flat grouped list uses [HbNavigationGroup].
 */
@Composable
fun HbTreeItem(
    label: String,
    depth: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    val colors = HbThemeTokens.colors
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.controlHeight)
            .clip(HbShapes.Small)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .clickable(role = Role.Button) { onExpandedChange(!expanded) }
            .semantics {
                if (expanded) {
                    collapse { onExpandedChange(false); true }
                } else {
                    expand { onExpandedChange(true); true }
                }
                stateDescription?.let { this.stateDescription = it }
            }
            .padding(
                start = INDENT_STEP * depth.toFloat() + HbSpacing.Xs,
                end = HbSpacing.Xs,
                top = HbSpacing.Xxs,
                bottom = HbSpacing.Xxs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        HbChevronGlyph(color = colors.text.secondary, expanded = expanded)
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { colors.text.primary },
        )
    }
}


private val INDENT_STEP = 16.dp
