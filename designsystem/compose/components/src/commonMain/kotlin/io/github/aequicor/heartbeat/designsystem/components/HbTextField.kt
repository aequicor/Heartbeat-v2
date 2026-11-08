package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Text input built on foundation, without a Material dependency. Single-line by default.
 *
 * Accessibility: [label] is the accessible name of the field, independent of [placeholder], which
 * disappears after input; when the field is used inside [HbFormField] without its own label, the
 * label and the error of the form field are read from the composition and become the accessible
 * name and error of the field. [errorMessage] both marks the field as invalid for accessibility
 * services and drives the error visuals, so the message is not encoded only in the border color.
 *
 * States: the value uses the primary text role, so it stays readable in both appearances; disabled
 * dims the value, the placeholder and the trailing action together and passes `enabled = false` to
 * the [trailing] slot, so a nested action can disable itself instead of merely looking disabled;
 * read-only keeps the readable value, the normal border and the focus outline, so it is
 * distinguishable from disabled; focused draws the separate focus outline outside the field; a
 * non-null error keeps the error border, so focused and invalid are visible at the same time.
 *
 * The owner keeps the value: [keyboardOptions] carries the keyboard type and IME action, and
 * [keyboardActions] handles that action, so a field integrates with the host input methods without
 * the kit owning the text or the submit behavior.
 */
@Composable
fun HbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    errorMessage: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (enabled: Boolean) -> Unit = {},
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val formLabel = HbFormFieldLocals.LocalLabel.current
    val effectiveLabel = label.ifEmpty { formLabel.orEmpty() }
    val effectiveError = errorMessage ?: HbFormFieldLocals.LocalError.current
    val visuals = fieldVisuals(enabled = enabled, error = effectiveError, colors = colors)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        if (formLabel == null) {
            HbFieldLabel(label = label)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .hbFocusRing(
                    focused = enabled && focused,
                    shape = HbShapes.Medium,
                    color = colors.focus,
                )
                .fieldSemantics(label = effectiveLabel, error = effectiveError)
                .clip(HbShapes.Medium)
                .background(colors.surface.input)
                .border(width = visuals.border.width, color = visuals.border.color, shape = HbShapes.Medium)
                .heightIn(min = HbSizes.controlHeight)
                .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
            textStyle = HbTypography.Body.copy(color = visuals.content),
            cursorBrush = SolidColor(visuals.content),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            readOnly = readOnly,
            enabled = enabled,
            decorationBox = { innerTextField ->
                HbFieldDecoration(
                    value = value,
                    placeholder = placeholder,
                    placeholderColor = visuals.placeholder,
                    enabled = enabled,
                    trailing = trailing,
                    innerTextField = innerTextField,
                )
            },
        )
    }
}

private data class FieldBorder internal constructor(
    val color: Color,
    val width: Dp,
)

private class FieldVisuals(
    val border: FieldBorder,
    val content: Color,
    val placeholder: Color,
)

private fun fieldVisuals(enabled: Boolean, error: String?, colors: HbColors): FieldVisuals {
    val border = when {
        error != null -> FieldBorder(colors.status.error.foreground, HbComponentStates.focusedBorderWidth)
        !enabled -> FieldBorder(colors.border.subtle, FIELD_BORDER_WIDTH)
        else -> FieldBorder(colors.border.control, FIELD_BORDER_WIDTH)
    }
    val content = if (enabled) {
        colors.text.primary
    } else {
        colors.text.primary.copy(alpha = HbEffects.DisabledContent)
    }
    val placeholder = when {
        error != null -> colors.status.error.foreground
        !enabled -> colors.text.secondary.copy(alpha = HbEffects.DisabledContent)
        else -> colors.text.secondary
    }
    return FieldVisuals(border = border, content = content, placeholder = placeholder)
}

/** Permanent label of the field; an empty label renders nothing and keeps the form-field contract. */
@Composable
private fun HbFieldLabel(label: String) {
    if (label.isEmpty()) return
    val color = HbTheme.colors.text.secondary
    BasicText(
        text = label,
        style = HbTypography.Label,
        color = { color },
    )
}

private fun Modifier.fieldSemantics(label: String, error: String?): Modifier = semantics {
    if (label.isNotEmpty()) contentDescription = label
    if (error != null) this.error(error)
}

private val FIELD_BORDER_WIDTH = 1.dp

@Composable
private fun HbFieldDecoration(
    value: String,
    placeholder: String,
    placeholderColor: Color,
    enabled: Boolean,
    trailing: @Composable (enabled: Boolean) -> Unit,
    innerTextField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The placeholder and the editor share one Box, so the hint overlays the input area
    // instead of being laid out next to the editor; the trailing slot stays outside the
    // editor, so its action does not edit text and dims together with the disabled field.
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                BasicText(
                    text = placeholder,
                    color = { placeholderColor },
                    style = HbTypography.Caption,
                )
            }
            innerTextField()
        }
        Box(
            modifier = Modifier.alpha(if (enabled) 1f else HbEffects.DisabledContent),
        ) {
            trailing(enabled)
        }
    }
}
