package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.hb_no_options
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import org.jetbrains.compose.resources.stringResource

/**
 * Searchable selection from a list (HB-DS-018).
 *
 * A combobox is not a plain action menu: the query is editable, the owner filters the options and
 * decides debouncing, and the popup keeps the keyboard contract of [HbOptionList]. Enter accepts
 * the active suggestion before anything broader, and Escape dismisses the suggestions before it
 * reaches a surrounding surface. The popup is non-focusable so opening the suggestions never moves
 * the focus away from the editor; when the editor loses focus, for example on Tab, the suggestions
 * close instead of staying over the next control, while a pointer click still reaches an option
 * because the popup itself does not take focus. [noResultsText] overrides the localized no-results
 * message that the component shows by default.
 *
 * The editor carries the combobox contract of the suggestions: the expanded and collapsed states,
 * their accessibility actions, and the active suggestion (or the no-results text) as the state
 * description, so a screen reader hears the same surface the mouse and keyboard users see.
 * [optionKey] is the stable identity of an option; it must be unique in [options] and saveable via
 * Bundle on Android. The active option is resolved by that identity, so a filtered list that keeps
 * its length but changes its order cannot move Enter to another value.
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
    optionKey: (T) -> Any? = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    var activeKey by remember { mutableStateOf<HbOptionIdentity?>(null) }
    val noResults = noResultsText ?: stringResource(Res.string.hb_no_options)
    // The stored identity can outlive its options after an asynchronous filtering pass.
    val active = activeOptionIndex(options, activeKey, optionKey)

    DisabledStateSync(enabled) { expanded = false }

    fun open() {
        if (!enabled) return
        if (activeKey == null) {
            activeKey = options.firstOrNull()?.let { optionIdentityOf(it, optionKey) }
        }
        expanded = true
    }

    Box(modifier = modifier) {
        HbTextField(
            value = query,
            onValueChange = { value ->
                onQueryChange(value)
                activeKey = options.firstOrNull()?.let { optionIdentityOf(it, optionKey) }
                expanded = true
            },
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            modifier = Modifier.comboboxEditorModifier(
                options = options,
                activeIndex = active,
                expanded = expanded,
                enabled = enabled,
                activeSuggestion = options.getOrNull(active)?.let(optionLabel),
                noResultsText = noResults,
                onExpand = { open() },
                onDismiss = { expanded = false },
                onActiveChange = { index ->
                    activeKey = options.getOrNull(index)?.let { optionIdentityOf(it, optionKey) }
                },
                onSelect = { option ->
                    onSelect(option)
                    expanded = false
                },
                onFocusChange = { focused ->
                    if (focused) open() else expanded = false
                },
            ),
        )

        if (expanded && enabled) {
            ComboboxPopup(
                options = options,
                activeIndex = active,
                optionLabel = optionLabel,
                noResultsText = noResults,
                optionKey = optionKey,
                offset = with(LocalDensity.current) { POPUP_OFFSET.roundToPx() },
                onSelect = { option ->
                    onSelect(option)
                    expanded = false
                },
                onActiveChange = { index ->
                    activeKey = options.getOrNull(index)?.let { optionIdentityOf(it, optionKey) }
                },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun DisabledStateSync(enabled: Boolean, onDisable: () -> Unit) {
    key(enabled) {
        SideEffect {
            if (!enabled) onDisable()
        }
    }
}

@Composable
private fun <T> ComboboxPopup(
    options: List<T>,
    activeIndex: Int,
    optionLabel: (T) -> String,
    noResultsText: String,
    optionKey: (T) -> Any?,
    offset: Int,
    onSelect: (T) -> Unit,
    onActiveChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = HbAnchoredPopupPositionProvider(gap = offset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
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
                // The editor keeps the focus, so the suggestions never take it from typing.
                focusOnOpen = false,
                optionKey = optionKey,
            )
        }
    }
}

@Composable
private fun NoResults(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    HbOptionSurface(modifier = modifier) { _ ->
        BasicText(
            text = text,
            style = HbTypography.Caption,
            color = { colors.text.secondary },
            modifier = Modifier.padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        )
    }
}

private fun <T> Modifier.comboboxEditorModifier(
    options: List<T>,
    activeIndex: Int,
    expanded: Boolean,
    enabled: Boolean,
    activeSuggestion: String?,
    noResultsText: String,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onActiveChange: (Int) -> Unit,
    onSelect: (T) -> Unit,
    onFocusChange: (Boolean) -> Unit,
): Modifier = this
    .fillMaxWidth()
    .onFocusChanged { state ->
        if (enabled) onFocusChange(state.hasFocus)
    }
    .comboboxKeys(
        options = options,
        activeIndex = activeIndex,
        expanded = expanded,
        onExpand = onExpand,
        onDismiss = onDismiss,
        onActiveChange = onActiveChange,
        onSelect = onSelect,
    )
    .comboboxSemantics(
        expanded = expanded,
        enabled = enabled,
        activeSuggestion = activeSuggestion,
        noResultsText = noResultsText,
        onExpand = onExpand,
        onCollapse = onDismiss,
    )

/**
 * Combobox contract of the editor: the expanded and collapsed actions, and the active suggestion
 * (or the no-results text) as the announced state of the suggestions.
 */
private fun Modifier.comboboxSemantics(
    expanded: Boolean,
    enabled: Boolean,
    activeSuggestion: String?,
    noResultsText: String,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
): Modifier = semantics {
    if (expanded) {
        stateDescription = activeSuggestion ?: noResultsText
        collapse {
            onCollapse()
            true
        }
    } else if (enabled) {
        expand {
            onExpand()
            true
        }
    }
}

private fun <T> Modifier.comboboxKeys(
    options: List<T>,
    activeIndex: Int,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onActiveChange: (Int) -> Unit,
    onSelect: (T) -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    comboboxKeyEvent(
        event = event,
        options = options,
        activeIndex = activeIndex,
        expanded = expanded,
        onExpand = onExpand,
        onDismiss = onDismiss,
        onActiveChange = onActiveChange,
        onSelect = onSelect,
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
            if (options.isNotEmpty()) {
                onActiveChange((activeIndex + 1).coerceAtMost(options.lastIndex))
            }
            true
        }

        Key.DirectionUp -> {
            if (options.isNotEmpty()) {
                onActiveChange((activeIndex - 1).coerceAtLeast(0))
            }
            true
        }

        Key.Enter -> {
            if (!expanded) {
                false
            } else {
                val option = options.getOrNull(activeIndex)
                if (option != null) {
                    onSelect(option)
                    onDismiss()
                }
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
