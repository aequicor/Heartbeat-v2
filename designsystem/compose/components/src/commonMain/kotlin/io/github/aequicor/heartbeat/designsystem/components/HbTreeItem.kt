@file:Suppress("NoNullableLambdaWithDefaultNull")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Row of a tree (HB-DS-019).
 *
 * A row with children exposes the expanded state through `expand` and `collapse` actions; a leaf row
 * passes `onExpandedChange = null`, so it has no chevron and no false expandable action. A leaf can
 * activate through [onClick] and report a chosen [selected] state through semantics and the
 * selection surface, so the tree works for navigation and item choice. [depth] indents the label
 * without encoding the hierarchy in color. [stateDescription] carries the localized state name. A
 * tree is used only where the hierarchy helps; a flat grouped list uses [HbNavigationGroup].
 */
@Composable
fun HbTreeItem(
    label: String,
    depth: Int,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    stateDescription: String? = null,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val expandable = onExpandedChange != null
    val activatable = onClick != null && !expandable
    val background = if (selected) colors.selection.background else Color.Transparent
    val contentColor = if (selected) colors.selection.foreground else colors.text.primary

    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = HbSizes.controlHeight)
        .clip(HbShapes.Small)
        .background(background)
        .onFocusChanged { focused = it.isFocused }
        .hbFocusRing(
            focused = focused && (expandable || activatable),
            shape = HbShapes.Small,
            color = colors.focus,
        )
        .then(
            when {
                expandable -> Modifier.clickable(role = Role.Button) { onExpandedChange(!expanded) }
                activatable -> Modifier.clickable(role = Role.Button, onClick = onClick)
                else -> Modifier
            },
        )
        .semantics {
            if (expandable) {
                if (expanded) {
                    collapse { onExpandedChange(false); true }
                } else {
                    expand { onExpandedChange(true); true }
                }
            }
            if (selected) this.selected = true
            stateDescription?.let { this.stateDescription = it }
        }
        .padding(
            start = INDENT_STEP * depth.toFloat() + HbSpacing.Xs,
            end = HbSpacing.Xs,
            top = HbSpacing.Xxs,
            bottom = HbSpacing.Xxs,
        )

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        if (expandable) {
            HbChevronGlyph(color = colors.text.secondary, expanded = expanded)
        }
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { contentColor },
        )
    }
}

private val INDENT_STEP = 16.dp
