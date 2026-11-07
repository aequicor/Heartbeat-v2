package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Text input built on foundation, without a Material dependency. Single-line by default. */
@Composable
fun HbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val colors = HbThemeDefaults.LocalColors.current

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(HbShapes.Small)
            .background(colors.surface)
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
        textStyle = HbTypography.Body,
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
                        color = { colors.onSurface },
                        style = HbTypography.Caption,
                    )
                }
                innerTextField()
            }
        },
    )
}
