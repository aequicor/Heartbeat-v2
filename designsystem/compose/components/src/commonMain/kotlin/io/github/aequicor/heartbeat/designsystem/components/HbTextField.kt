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
 * dims the value; the placeholder uses the secondary text role rather than the disabled role;
 * focused draws the separate focus outline outside the field; a non-null error keeps the error
 * border, so focused and invalid are visible at the same time.
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
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable () -> Unit = {},
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }
    val formLabel = HbFormFieldLocals.LocalLabel.current
    val effectiveLabel = label.ifEmpty { formLabel.orEmpty() }
    val effectiveError = errorMessage ?: HbFormFieldLocals.LocalError.current
    val border = fieldBorder(effectiveError, enabled, colors)
    val contentColor = if (enabled) colors.text.primary else colors.text.primary.copy(alpha = HbEffects.DisabledContent)
    val placeholderColor = if (effectiveError != null) {
        colors.status.error.foreground
    } else {
        colors.text.secondary
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        if (label.isNotEmpty() && formLabel == null) {
            BasicText(
                text = label,
                style = HbTypography.Label,
                color = { colors.text.secondary },
            )
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
                .border(width = border.width, color = border.color, shape = HbShapes.Medium)
                .heightIn(min = HbSizes.controlHeight)
                .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
            textStyle = HbTypography.Body.copy(color = contentColor),
            cursorBrush = SolidColor(contentColor),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            enabled = enabled,
            decorationBox = { innerTextField ->
                HbFieldDecoration(
                    value = value,
                    placeholder = placeholder,
                    placeholderColor = placeholderColor,
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

private fun fieldBorder(error: String?, enabled: Boolean, colors: HbColors): FieldBorder = when {
    error != null -> FieldBorder(colors.status.error.foreground, HbComponentStates.focusedBorderWidth)
    !enabled -> FieldBorder(colors.border.subtle, FIELD_BORDER_WIDTH)
    else -> FieldBorder(colors.border.control, FIELD_BORDER_WIDTH)
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
    trailing: @Composable () -> Unit,
    innerTextField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The placeholder and the editor share one Box, so the hint overlays the input area
    // instead of being laid out next to the editor; the trailing slot stays outside the
    // editor, so its action does not edit text.
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
        trailing()
    }
}
