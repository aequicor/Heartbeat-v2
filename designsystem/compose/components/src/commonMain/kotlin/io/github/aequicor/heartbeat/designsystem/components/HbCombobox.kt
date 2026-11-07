package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Searchable selection from a list (HB-DS-018).
 *
 * A combobox is not a plain action menu: the query is editable, the owner filters the options and
 * decides debouncing, and the popup keeps the keyboard contract of [HbOptionList]. Enter accepts
 * the active suggestion before anything broader, and Escape dismisses the suggestions before it
 * reaches a surrounding surface. [noResultsText] renders the no-results state inside the popup.
 */
@Composable
fun <T> HbCombobox(
    options: List<T>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    optionLabel: (T) -> String = { it.toString() },
    noResultsText: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableIntStateOf(0) }
    val popupOffset = with(LocalDensity.current) { POPUP_OFFSET.roundToPx() }

    Box(modifier = modifier) {
        HbTextField(
            value = query,
            onValueChange = { value ->
                onQueryChange(value)
                expanded = true
                activeIndex = 0
            },
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) expanded = true }
                .onPreviewKeyEvent { event ->
                    comboboxKeyEvent(
                        event = event,
                        options = options,
                        expanded = expanded,
                        activeIndex = activeIndex,
                        onExpand = { expanded = true },
                        onDismiss = { expanded = false },
                        onActiveChange = { activeIndex = it },
                        onSelect = {
                            onSelect(it)
                            expanded = false
                        },
                    )
                },
        )

        if (expanded) {
            ComboboxPopup(
                options = options,
                activeIndex = activeIndex,
                optionLabel = optionLabel,
                noResultsText = noResultsText,
                offset = popupOffset,
                onSelect = { option ->
                    onSelect(option)
                    expanded = false
                },
                onActiveChange = { activeIndex = it },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun <T> ComboboxPopup(
    options: List<T>,
    activeIndex: Int,
    optionLabel: (T) -> String,
    noResultsText: String?,
    offset: Int,
    onSelect: (T) -> Unit,
    onActiveChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = HbAnchoredPopupPositionProvider(gap = offset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        if (options.isEmpty()) {
            NoResults(text = noResultsText)
        } else {
            HbOptionList(
                options = options,
                selected = null,
                activeIndex = activeIndex,
                optionLabel = optionLabel,
                onSelect = onSelect,
                onActiveChange = onActiveChange,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun NoResults(
    text: String?,
    modifier: Modifier = Modifier,
) {
    if (text == null) {
        return
    }
    val colors = HbTheme.colors

    BasicText(
        text = text,
        style = HbTypography.Caption,
        color = { colors.text.secondary },
        modifier = modifier.padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
    )
}

/**
 * Keyboard contract of the suggestions: arrows move the active option, Enter accepts it first, and
 * Escape dismisses the suggestions before anything broader.
 */
private fun <T> comboboxKeyEvent(
    event: KeyEvent,
    options: List<T>,
    expanded: Boolean,
    activeIndex: Int,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onActiveChange: (Int) -> Unit,
    onSelect: (T) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionDown -> {
            onExpand()
            onActiveChange((activeIndex + 1).coerceAtMost(options.lastIndex))
            true
        }

        Key.DirectionUp -> {
            onActiveChange((activeIndex - 1).coerceAtLeast(0))
            true
        }

        Key.Enter -> {
            if (!expanded) {
                false
            } else {
                options.getOrNull(activeIndex)?.let(onSelect)
                onDismiss()
                true
            }
        }

        Key.Escape -> {
            if (!expanded) {
                false
            } else {
                onDismiss()
                true
            }
        }

        else -> false
    }
}


private val POPUP_OFFSET = 4.dp
