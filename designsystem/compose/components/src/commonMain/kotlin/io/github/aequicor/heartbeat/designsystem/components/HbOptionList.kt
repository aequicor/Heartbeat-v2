@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Scrollable list of options shared by the choice surfaces (HB-DS-019).
 *
 * The list owns keyboard navigation inside the popup: arrows move the active option, Enter selects
 * it and Escape dismisses. It is a container of `HbMenuItem` rows, so an option is an action with a
 * chosen state, not a second kind of control. The active option receives the focus when the popup
 * opens, is kept inside the visible area and is marked with a permanent indicator, so it is not
 * conveyed by a low-contrast tint alone.
 *
 * The active index is normalized against the current options on every composition: when the owner
 * shrinks an asynchronously filtered list, the highlight and Enter keep pointing at a real row.
 */
@Composable
internal fun <T> HbOptionList(
    options: List<T>,
    selected: T?,
    activeIndex: Int,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onActiveChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
    focusOnOpen: Boolean = true,
) {
    val colors = HbTheme.colors
    val active = activeIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val bringIntoViewRequesters = remember(options.size) { List(options.size) { BringIntoViewRequester() } }
    val focusRequesters = remember(options.size) { List(options.size) { FocusRequester() } }

    LaunchedEffect(active) {
        bringIntoViewRequesters.getOrNull(active)?.bringIntoView()
    }
    key(active, focusOnOpen) {
        SideEffect {
            // The focus follows the active option so a keyboard or screen-reader user stays on the
            // row that Enter would choose. A non-focusable popup keeps the focus in its editor.
            if (focusOnOpen) focusRequesters.getOrNull(active)?.requestFocus()
        }
    }

    HbOptionSurface(modifier = modifier) { maxHeight ->
        if (options.isEmpty() && emptyText != null) {
            BasicText(
                text = emptyText,
                style = HbTypography.Caption,
                color = { colors.text.secondary },
                modifier = Modifier.padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
            )
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(HbSpacing.Xxs)
                    .semantics { collectionInfo = CollectionInfo(rowCount = options.size, columnCount = 1) }
                    .onPreviewKeyEvent { event ->
                        optionListKeyEvent(
                            event = event,
                            options = options,
                            active = active,
                            onActiveChange = onActiveChange,
                            onSelect = onSelect,
                            onDismiss = onDismiss,
                        )
                    },
            ) {
                options.forEachIndexed { index, option ->
                    HbMenuItem(
                        label = optionLabel(option),
                        onClick = { onSelect(option) },
                        selected = option == selected,
                        modifier = optionRowModifier(
                            active = index == active,
                            requester = focusRequesters[index],
                            bringIntoView = bringIntoViewRequesters[index],
                            colors = colors,
                        ),
                    )
                }
            }
        }
    }
}

/** Row of the list: focus follows the active option; the active row carries a permanent indicator. */
private fun optionRowModifier(
    active: Boolean,
    requester: FocusRequester,
    bringIntoView: BringIntoViewRequester,
    colors: HbColors,
): Modifier = Modifier
    .bringIntoViewRequester(bringIntoView)
    .focusRequester(requester)
    .then(
        if (!active) {
            Modifier
        } else {
            Modifier
                .background(colors.surface.subtle)
                .drawBehind {
                    val width = ACTIVE_INDICATOR.toPx()
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                    drawRect(
                        color = colors.selection.indicator,
                        topLeft = Offset(x, 0f),
                        size = Size(width, size.height),
                    )
                }
        },
    )

private fun <T> optionListKeyEvent(
    event: KeyEvent,
    options: List<T>,
    active: Int,
    onActiveChange: (Int) -> Unit,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionDown -> {
            if (options.isNotEmpty()) onActiveChange((active + 1).coerceAtMost(options.lastIndex))
            options.isNotEmpty()
        }

        Key.DirectionUp -> {
            if (options.isNotEmpty()) onActiveChange((active - 1).coerceAtLeast(0))
            options.isNotEmpty()
        }

        Key.Enter -> {
            val option = options.getOrNull(active)
            if (option != null) {
                onSelect(option)
                true
            } else {
                false
            }
        }

        Key.Escape -> {
            onDismiss()
            true
        }

        else -> false
    }
}

/**
 * Shared container of the choice surfaces (HB-DS-019): the raised background and the subtle border,
 * bounded by the window constraints. The container never demands a minimum width that the window
 * cannot give and never exceeds the remaining height, so a popup in a short or narrow window stays
 * reachable instead of being clipped by the screen edge.
 */
@Composable
internal fun HbOptionSurface(
    modifier: Modifier = Modifier,
    content: @Composable (maxHeight: Dp) -> Unit,
) {
    val colors = HbTheme.colors

    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val minWidth = minOf(POPUP_MIN_WIDTH, availableWidth)
        val maxWidth = minOf(POPUP_MAX_WIDTH, availableWidth)
        val height = if (availableHeight.value.isFinite()) {
            minOf(POPUP_MAX_HEIGHT, availableHeight)
        } else {
            POPUP_MAX_HEIGHT
        }

        Box(
            modifier = Modifier
                .widthIn(min = minWidth, max = maxWidth)
                .clip(HbShapes.Medium)
                .background(colors.surface.raised)
                .border(POPUP_BORDER, colors.border.subtle, HbShapes.Medium),
        ) {
            content(height)
        }
    }
}

private val POPUP_MIN_WIDTH = 200.dp

private val POPUP_MAX_WIDTH = 420.dp

private val POPUP_MAX_HEIGHT = 280.dp

private val POPUP_BORDER = 1.dp

private val ACTIVE_INDICATOR = 2.dp
