package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.Typography

/** Body text in the theme color, without a Material dependency. */
@Composable
fun HbText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = HbThemeDefaults.LocalColors.current

    BasicText(
        text = text,
        modifier = modifier,
        color = { colors.onSurface },
        style = Typography.Body,
    )
}
