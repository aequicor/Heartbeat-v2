package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Tab strip that switches peer content in the same context (HB-DS-019).
 *
 * Selection is exposed by the `selectable` semantics and marked by an underline drawn across the
 * measured tab, so it is not encoded by color alone and a horizontally scrolling strip keeps the
 * indicator aligned. The strip is one keyboard group: the left and right arrows move the selection
 * and the focus together, and a long strip scrolls instead of shrinking its labels.
 */
@Composable
fun <T> HbTabs(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tabLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val focusRequesters = remember(tabs.size) { List(tabs.size) { FocusRequester() } }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val current = tabs.indexOf(selected)
                val target = when (event.key) {
                    Key.DirectionRight -> (current + 1) % tabs.size
                    Key.DirectionLeft -> (current - 1 + tabs.size) % tabs.size
                    else -> return@onPreviewKeyEvent false
                }
                focusRequesters[target].requestFocus()
                onSelect(tabs[target])
                true
            },
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        tabs.forEachIndexed { index, tab ->
            HbTab(
                label = tabLabel(tab),
                selected = tab == selected,
                enabled = enabled,
                onClick = { onSelect(tab) },
                modifier = Modifier.focusRequester(focusRequesters[index]),
            )
        }
    }
}

@Composable
private fun HbTab(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbThemeDefaults.LocalColors.current
    var focused by remember { mutableStateOf(false) }
    val contentColor = when {
        !enabled -> colors.text.primary.copy(HbEffects.DisabledContent)
        selected -> colors.text.primary
        else -> colors.text.secondary
    }

    Column(
        modifier = modifier
            .clip(HbShapes.Small)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs)
            .drawBehind {
                if (selected) {
                    val stroke = TAB_INDICATOR.toPx()
                    val y = size.height - stroke / 2f
                    drawLine(
                        color = colors.accent.foreground,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { contentColor },
        )
    }
}

private val TAB_INDICATOR = 2.dp
