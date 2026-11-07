package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults

/**
 * Compact tone indicator of a status (HB-DS-006). The dot never carries the only meaning: a status
 * row, badge or label always names the state beside it.
 */
@Composable
fun HbStatusDot(
    modifier: Modifier = Modifier,
    tone: HbStatusTone = HbStatusTone.Neutral,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Box(
        modifier = modifier
            .size(DOT_SIZE)
            .clip(CircleShape)
            .background(tone.foreground(colors)),
    )
}

private val DOT_SIZE = 8.dp
