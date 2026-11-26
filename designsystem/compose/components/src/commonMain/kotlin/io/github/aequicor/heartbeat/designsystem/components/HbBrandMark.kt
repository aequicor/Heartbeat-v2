package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.resources.Res
import io.github.aequicor.heartbeat.designsystem.resources.heartbeat_lab_logo
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

@Composable
public fun HbBrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.heartbeat_lab_logo),
        contentDescription = HbAppName,
        modifier = modifier.size(width = 139.dp, height = 23.dp),
    )
}
