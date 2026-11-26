package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.heartbeat_lab_mark
import org.jetbrains.compose.resources.painterResource

public const val HbAppName: String = "Heartbeat Lab"

@Composable
public fun HbBrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.heartbeat_lab_mark),
        contentDescription = null,
        modifier = modifier,
    )
}
