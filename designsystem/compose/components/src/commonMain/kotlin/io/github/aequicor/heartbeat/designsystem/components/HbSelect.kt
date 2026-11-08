@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.hb_no_options
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import org.jetbrains.compose.resources.stringResource

/**
 * Selection from a closed list (HB-DS-018).
 *
 * The trigger is a field-shaped control with the `DropdownList` role; the options open in a
 * focusable popup with keyboard navigation. The options are supplied by the owner, so the component
 * does not query a model catalog or decide availability. The trigger announces its label, the
 * selected value and the expanded state through semantics, keeps the shared focus outline, closes
 * the popup when it becomes disabled, and reads the label and error of the surrounding
 * [HbFormField].
 *
 * Both ways of opening the popup — a pointer click and the accessibility `expand` action — run the
 * same [open] function, so the active option always starts at the selected value. Every way of
 * closing the popup runs the same close procedure, including Escape and the platform dismissal, so
 * the focus returns to the trigger that opened the surface.
 *
 * [optionKey] is the stable identity of an option and must be unique in [options]. The active
 * option is resolved by that identity instead of by its position, so an asynchronous refresh that
 * keeps the list length but changes the order does not move the highlight or Enter to another value.
 */
@Composable
fun <T> HbSelect(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    optionLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    optionKey: (T) -> Any? = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    var activeKey by remember { mutableStateOf<HbOptionIdentity?>(null) }
    val triggerRequester = remember { FocusRequester() }
    val popupOffset = with(LocalDensity.current) { POPUP_OFFSET.roundToPx() }
    val noOptionsText = stringResource(Res.string.hb_no_options)
    val formLabel = HbFormFieldLocals.LocalLabel.current
    val formError = HbFormFieldLocals.LocalError.current
    val effectiveLabel = label.ifEmpty { formLabel.orEmpty() }
    // The stored identity can outlive its options after an asynchronous refresh of the list.
    val active = activeOptionIndex(options, activeKey, optionKey)

    key(enabled) {
        SideEffect {
            if (!enabled) expanded = false
        }
    }

    fun open() {
        if (!enabled || expanded) return
        activeKey = selected?.let { optionIdentityOf(it, optionKey) }
            ?: options.firstOrNull()?.let { optionIdentityOf(it, optionKey) }
        expanded = true
    }

    fun close() {
        expanded = false
        triggerRequester.requestFocus()
    }

    Box(modifier = modifier) {
        SelectTrigger(
            text = selected?.let(optionLabel) ?: placeholder,
            isPlaceholder = selected == null,
            expanded = expanded,
            enabled = enabled,
            label = effectiveLabel,
            value = selected?.let(optionLabel),
            error = formError,
            focusRequester = triggerRequester,
            onClick = {
                selectToggle(expanded, ::open, ::close)
            },
            onExpandedChange = { shouldExpand ->
                selectExpanded(shouldExpand, ::open, ::close)
            },
        )

        if (expanded && enabled) {
            SelectPopup(
                options = options,
                selected = selected,
                activeIndex = active,
                optionLabel = optionLabel,
                noOptionsText = noOptionsText,
                optionKey = optionKey,
                offset = popupOffset,
                onSelect = { option ->
                    onSelect(option)
                    close()
                },
                onActiveChange = { index ->
                    activeKey = options.getOrNull(index)?.let { optionIdentityOf(it, optionKey) }
                },
                onClose = { close() },
            )
        }
    }
}

@Composable
private fun <T> SelectPopup(
    options: List<T>,
    selected: T?,
    activeIndex: Int,
    optionLabel: (T) -> String,
    noOptionsText: String,
    optionKey: (T) -> Any?,
    offset: Int,
    onSelect: (T) -> Unit,
    onActiveChange: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Popup(
        popupPositionProvider = remember(offset) { HbAnchoredPopupPositionProvider(gap = offset) },
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        HbOptionList(
            options = options,
            selected = selected,
            activeIndex = activeIndex,
            optionLabel = optionLabel,
            onSelect = onSelect,
            onActiveChange = onActiveChange,
            onDismiss = onClose,
            emptyText = noOptionsText,
            optionKey = optionKey,
        )
    }
}

@Composable
private fun SelectTrigger(
    text: String,
    isPlaceholder: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    label: String,
    value: String?,
    error: String?,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val border = when {
        !enabled -> colors.border.subtle
        error != null -> colors.status.error.foreground
        else -> colors.border.control
    }
    val contentColor = when {
        !enabled -> colors.text.disabled
        isPlaceholder -> colors.text.secondary
        else -> colors.text.primary
    }
    val chevronColor = if (enabled) colors.text.secondary else colors.text.disabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.controlHeight)
            .onFocusChanged { focused = it.isFocused }
            .hbFocusRing(focused = enabled && focused, shape = HbShapes.Medium, color = colors.focus)
            .clip(HbShapes.Medium)
            .background(colors.surface.input)
            .border(FIELD_BORDER, border, HbShapes.Medium)
            .focusRequester(focusRequester)
            .clickable(
                enabled = enabled,
                role = Role.DropdownList,
                onClick = onClick,
            )
            .selectSemantics(
                enabled = enabled,
                label = label,
                value = value,
                expanded = expanded,
                error = error,
                onExpandedChange = onExpandedChange,
            )
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = text,
            style = HbTypography.Label,
            color = { contentColor },
            modifier = Modifier.weight(1f),
        )
        HbChevronGlyph(color = chevronColor, expanded = expanded)
    }
}

private fun Modifier.selectSemantics(
    enabled: Boolean,
    label: String,
    value: String?,
    expanded: Boolean,
    error: String?,
    onExpandedChange: (Boolean) -> Unit,
): Modifier = semantics {
    if (label.isNotEmpty()) contentDescription = label
    if (value != null) stateDescription = value
    if (enabled) {
        if (expanded) {
            collapse { onExpandedChange(false); true }
        } else {
            expand { onExpandedChange(true); true }
        }
    }
    if (error != null) this.error(error)
}


private fun selectToggle(expanded: Boolean, open: () -> Unit, close: () -> Unit) {
    if (expanded) close() else open()
}

private fun selectExpanded(shouldExpand: Boolean, open: () -> Unit, close: () -> Unit) {
    if (shouldExpand) open() else close()
}


private val FIELD_BORDER = 1.dp

private val POPUP_OFFSET = 4.dp
