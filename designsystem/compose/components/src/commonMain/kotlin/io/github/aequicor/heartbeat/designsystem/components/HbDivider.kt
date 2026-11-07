package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults

/**
 * Separator between surfaces or form groups. A divider is a decorative boundary: it separates
 * content and never identifies a control on its own (HB-DS-004).
 */
@Composable
fun HbDivider(
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val colors = HbThemeDefaults.LocalColors.current

    if (vertical) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(dividerThickness)
                .background(colors.border.subtle),
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(dividerThickness)
                .background(colors.border.subtle),
        )
    }
}

private val dividerThickness = 1.dp
