@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Navigation row of the sidebar or rail (HB-DS-009, HB-DS-019). Selection is announced by the
 * `selectable` semantics and marked by a persistent indicator bar drawn across the row height, so
 * it is not encoded by the background color alone; keyboard focus draws its own outline over the
 * selected state. The row uses a minimum height, so a large text scale grows it instead of
 * clipping the label.
 */
@Composable
fun HbNavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val background = if (selected) colors.selection.background else Color.Transparent
    val contentColor = when {
        !enabled -> colors.text.primary.copy(HbEffects.DisabledContent)
        selected -> colors.selection.foreground
        else -> colors.text.primary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.navigationRowHeight)
            .clip(HbShapes.Small)
            .background(background)
            .drawBehind {
                if (selected) {
                    val width = indicatorWidth.toPx()
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                    drawRect(
                        color = colors.selection.indicator,
                        topLeft = Offset(x, 0f),
                        size = Size(width, size.height),
                    )
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                start = indicatorWidth + HbSpacing.S,
                end = HbSpacing.S,
                top = HbSpacing.Xxs,
                bottom = HbSpacing.Xxs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { contentColor },
        )
    }
}


private val indicatorWidth = 2.dp
