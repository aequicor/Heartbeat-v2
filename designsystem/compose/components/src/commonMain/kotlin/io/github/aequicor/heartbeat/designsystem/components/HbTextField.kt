package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
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

/** Single-line text input built on foundation, without a Material dependency. */
@Composable
fun HbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
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
        singleLine = true,
        enabled = enabled,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                BasicText(
                    text = placeholder,
                    color = { colors.onSurface },
                    style = HbTypography.Caption,
                )
            }
            innerTextField()
        },
    )
}
