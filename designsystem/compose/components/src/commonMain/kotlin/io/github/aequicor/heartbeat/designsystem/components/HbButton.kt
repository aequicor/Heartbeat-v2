package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Primary action button built on foundation, without a Material dependency. */
@Composable
fun HbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Box(
        modifier = modifier
            .clip(HbShapes.Medium)
            .background(colors.primary)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HbSpacing.M, vertical = HbSpacing.S),
    ) {
        BasicText(
            text = text,
            color = { colors.onPrimary },
            style = HbTypography.Body,
        )
    }
}
