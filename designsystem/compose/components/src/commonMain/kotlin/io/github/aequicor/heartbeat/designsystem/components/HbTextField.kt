package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Text input built on foundation, without a Material dependency. Single-line by default.
 *
 * States: the value always uses [HbThemeDefaults.LocalColors] `onSurface`, so the input stays
 * readable on a dark palette; disabled dims the value and the placeholder; focused draws a
 * primary border; [isError] draws an error border and colors the placeholder as the error.
 */
@Composable
fun HbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val colors = HbThemeDefaults.LocalColors.current
    var focused by remember { mutableStateOf(false) }

    val contentColor = if (enabled) {
        colors.onSurface
    } else {
        colors.onSurface.copy(alpha = HbComponentStates.DISABLED_CONTENT_ALPHA)
    }
    val placeholderColor = if (isError) colors.error else contentColor
    val borderColor = when {
        isError -> colors.error
        enabled && focused -> colors.primary
        else -> null
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(HbShapes.Small)
            .background(colors.surface)
            .then(
                if (borderColor != null) {
                    Modifier.border(
                        width = HbComponentStates.focusedBorderWidth,
                        color = borderColor,
                        shape = HbShapes.Small,
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
        enabled = enabled,
        decorationBox = { innerTextField ->
            // The placeholder and the editor share one Box, so the hint overlays
            // the input area instead of being laid out next to the editor.
            Box {
                if (value.isEmpty()) {
                    BasicText(
                        text = placeholder,
                        color = { placeholderColor },
                        style = HbTypography.Caption,
                    )
                }
                innerTextField()
            }
        },
    )
}
