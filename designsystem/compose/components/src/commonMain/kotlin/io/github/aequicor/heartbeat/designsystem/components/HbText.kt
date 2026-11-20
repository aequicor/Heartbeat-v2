package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Text in the primary color role; [style] defaults to reading text. */
@Composable
fun HbText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HbTypography.Body,
) {
    val colors = HbTheme.colors

    BasicText(
        text = text,
        modifier = modifier,
        color = { colors.text.primary },
        style = style,
    )
}
