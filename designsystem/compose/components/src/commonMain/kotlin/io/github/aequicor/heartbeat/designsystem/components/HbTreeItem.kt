@file:Suppress("NoNullableLambdaWithDefaultNull", "MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.hb_collapse
import io.github.aequicor.heartbeat.designsystem.resources.hb_expand
import io.github.aequicor.heartbeat.designsystem.resources.hb_tree_level
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import org.jetbrains.compose.resources.stringResource

/**
 * Row of a tree (HB-DS-019).
 *
 * A row with children exposes the expanded state; a leaf row passes `onExpandedChange = null`, so it
 * has no chevron and no false expandable action. Selection and expansion are separate operations: a
 * row that has both [onClick] and [onExpandedChange] activates on a row click and expands through its
 * own chevron control, so the node can be chosen without changing the branch. A row with children
 * and no activation keeps the single-action behavior and toggles on the whole row.
 *
 * [depth] is announced as a localized level in the state description next to the caller's
 * [stateDescription], because the Compose semantics contract has no platform-independent tree-level
 * property. The platform accessibility bridge then reads the branch position instead of only the
 * indentation.
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
    expandLabel: String? = null,
    collapseLabel: String? = null,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val expandable = onExpandedChange != null
    val activatable = onClick != null
    val expandActionLabel = expandLabel ?: stringResource(Res.string.hb_expand)
    val collapseActionLabel = collapseLabel ?: stringResource(Res.string.hb_collapse)
    val levelLabel = if (depth > 0) stringResource(Res.string.hb_tree_level, depth) else null
    val description = treeStateDescription(stateDescription, levelLabel)
    // One toggle action serves the pointer, the keyboard and the accessibility action.
    val toggleExpansion: (() -> Unit)? = onExpandedChange?.let { change -> { change(!expanded) } }
    val background = if (selected) colors.selection.background else Color.Transparent
    val contentColor = if (selected) colors.selection.foreground else colors.text.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.controlHeight)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(
                focused = focused && (expandable || activatable),
                shape = HbShapes.Small,
                color = colors.focus,
            )
            .clip(HbShapes.Small)
            .background(background)
            .then(treeRowInteraction(onClick = onClick, toggleExpansion = toggleExpansion))
            .treeRowSemantics(
                activatable = activatable,
                expanded = expanded,
                selected = selected,
                description = description,
                toggleExpansion = toggleExpansion,
            )
            .padding(
                start = INDENT_STEP * depth.toFloat() + HbSpacing.Xs,
                end = HbSpacing.Xs,
                top = HbSpacing.Xxs,
                bottom = HbSpacing.Xxs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        if (toggleExpansion != null) {
            TreeExpandControl(
                expanded = expanded,
                separate = activatable,
                description = if (expanded) collapseActionLabel else expandActionLabel,
                onClick = toggleExpansion,
            )
        }
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { contentColor },
        )
    }
}

private fun treeRowInteraction(
    onClick: (() -> Unit)?,
    toggleExpansion: (() -> Unit)?,
): Modifier = when {
    onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
    toggleExpansion != null -> Modifier.clickable(role = Role.Button, onClick = toggleExpansion)
    else -> Modifier
}

private fun Modifier.treeRowSemantics(
    activatable: Boolean,
    expanded: Boolean,
    selected: Boolean,
    description: String?,
    toggleExpansion: (() -> Unit)?,
): Modifier = semantics {
    if (!activatable && toggleExpansion != null) {
        if (expanded) {
            collapse { toggleExpansion(); true }
        } else {
            expand { toggleExpansion(); true }
        }
    }
    if (selected) this.selected = true
    description?.let { this.stateDescription = it }
}

/** Chevron of a branch: an independent expand control when the row itself activates. */
@Composable
private fun TreeExpandControl(
    expanded: Boolean,
    separate: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = HbTheme.colors
    if (!separate) {
        HbChevronGlyph(color = colors.text.secondary, expanded = expanded)
        return
    }
    Box(
        modifier = Modifier
            .size(HbSizes.minimumTarget)
            .clip(HbShapes.Small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        HbChevronGlyph(color = colors.text.secondary, expanded = expanded)
    }
}

private fun treeStateDescription(state: String?, level: String?): String? =
    listOfNotNull(state, level).joinToString(", ").ifEmpty { null }

private val INDENT_STEP = 16.dp
