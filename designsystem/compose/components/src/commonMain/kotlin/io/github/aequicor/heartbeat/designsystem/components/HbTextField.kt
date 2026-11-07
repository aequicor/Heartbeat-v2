package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Text input built on foundation, without a Material dependency. Single-line by default.
 *
 * Accessibility: [label] is the accessible name of the field, independent of [placeholder], which
 * disappears after input; [errorMessage] both marks the field as invalid for accessibility
 * services and drives the error visuals, so the message is not encoded only in the border color.
 *
 * States: the value uses the primary text role, so it stays readable in both appearances; disabled
 * dims the value; the placeholder uses the secondary text role rather than the disabled role;
 * focused draws the focus outline; a non-null [errorMessage] draws an error border in the
 * [io.github.aequicor.heartbeat.designsystem.theme.HbStatusPalette.error] role.
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
    val colors = HbThemeDefaults.LocalColors.current
    var focused by remember { mutableStateOf(false) }
    val isError = errorMessage != null

    val contentColor = if (enabled) {
        colors.text.primary
    } else {
        colors.text.primary.copy(alpha = HbEffects.DisabledContent)
    }
    val placeholderColor = if (isError) colors.status.error.foreground else colors.text.secondary
    val borderColor = when {
        isError -> colors.status.error.foreground
        enabled && focused -> colors.focus
        else -> null
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                if (label.isNotEmpty()) contentDescription = label
                if (errorMessage != null) error(errorMessage)
            }
            .clip(HbShapes.Medium)
            .background(colors.surface.input)
            .then(
                if (borderColor != null) {
                    Modifier.border(
                        width = HbComponentStates.focusedBorderWidth,
                        color = borderColor,
                        shape = HbShapes.Medium,
                    )
                } else {
                    Modifier
                },
            )
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
