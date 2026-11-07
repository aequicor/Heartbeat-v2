package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.aequicor.heartbeat.designsystem.theme.AppOnSurface
import io.github.aequicor.heartbeat.designsystem.theme.AppSurface
import io.github.aequicor.heartbeat.designsystem.theme.Shapes
import io.github.aequicor.heartbeat.designsystem.theme.Spacing
import io.github.aequicor.heartbeat.designsystem.theme.Typography

/** Single-line text input built on foundation, without a Material dependency. */
@Composable
fun AppTextField(
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
            .background(AppSurface)
            .padding(horizontal = Spacing.S, vertical = Spacing.Xs),
        textStyle = Typography.Body,
        singleLine = true,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                BasicText(
                    text = placeholder,
                    color = { AppOnSurface },
                    style = Typography.Caption,
                )
            }
            innerTextField()
        },
    )
}
