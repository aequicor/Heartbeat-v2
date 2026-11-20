package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The host's caption overlay and its interactive exclusions in Compose root pixels. */
class HbWindowCaption(
    val rightInset: Dp,
    val height: Dp,
    val leftInset: Dp = 0.dp,
    internal val clientArea: (key: Any, bounds: Rect?) -> Unit,
) {
    companion object {
        val current: HbWindowCaption
            @Composable
            @ReadOnlyComposable
            get() = LocalWindowCaption.current
    }
}

// Window geometry is shared by the host and independently routed feature content.
@Suppress("CompositionLocalAllowlist")
private val LocalWindowCaption = compositionLocalOf { HbWindowCaption(0.dp, 0.dp) { _, _ -> } }

@Composable
fun HbProvideWindowCaption(caption: HbWindowCaption, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWindowCaption provides caption, content = content)
}

/** Allows a control inside the caption band to receive pointer events as ordinary client content. */
@Composable
fun HbWindowClientArea(content: @Composable (Modifier) -> Unit) {
    val caption = HbWindowCaption.current
    val modifier = if (caption.height <= 0.dp) {
        Modifier
    } else {
        val key = remember { Any() }
        DisposableEffect(caption.clientArea, key) {
            onDispose { caption.clientArea(key, null) }
        }
        Modifier.onGloballyPositioned { caption.clientArea(key, it.boundsInRoot()) }
    }
    content(modifier)
}
