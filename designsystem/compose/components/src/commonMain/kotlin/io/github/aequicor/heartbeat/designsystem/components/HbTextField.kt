package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.aequicor.heartbeat.designsystem.theme.AppColors
import io.github.aequicor.heartbeat.designsystem.theme.Shapes
import io.github.aequicor.heartbeat.designsystem.theme.Spacing
import io.github.aequicor.heartbeat.designsystem.theme.Typography

/** Single-line text input built on foundation, without a Material dependency. */
@Composable
fun HbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(Shapes.Small)
            .background(AppColors.surface)
            .padding(horizontal = Spacing.S, vertical = Spacing.Xs),
        textStyle = Typography.Body,
        singleLine = true,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                BasicText(
                    text = placeholder,
                    color = { AppColors.onSurface },
                    style = Typography.Caption,
                )
            }
            innerTextField()
        },
    )
}
