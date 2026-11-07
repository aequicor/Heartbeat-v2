package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.aequicor.heartbeat.designsystem.theme.AppColors
import io.github.aequicor.heartbeat.designsystem.theme.Shapes
import io.github.aequicor.heartbeat.designsystem.theme.Spacing
import io.github.aequicor.heartbeat.designsystem.theme.Typography

/** Primary action button built on foundation, without a Material dependency. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(Shapes.Medium)
            .background(AppColors.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
    ) {
        BasicText(
            text = text,
            color = { AppColors.onPrimary },
            style = Typography.Body,
        )
    }
}
