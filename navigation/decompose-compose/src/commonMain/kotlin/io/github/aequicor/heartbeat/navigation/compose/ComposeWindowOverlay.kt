package io.github.aequicor.heartbeat.navigation.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility

private val LocalWindowOverlays = compositionLocalOf<MutableList<@Composable () -> Unit>?> { null }

/** Renders feature overlays across the entire shell, independently of navigation pane sizes. */
@Composable
public fun ComposeWindowOverlayHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val overlays = remember { mutableStateListOf<@Composable () -> Unit>() }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hideWhenCovered(overlays.isNotEmpty())) {
            CompositionLocalProvider(LocalWindowOverlays provides overlays, content = content)
        }
        overlays.forEachIndexed { index, overlay ->
            key(overlay) {
                Box(Modifier.fillMaxSize().hideWhenCovered(index != overlays.lastIndex)) { overlay() }
            }
        }
    }
}

/** Presents content in the same window above the shell, with the caller's composition locals. */
@Composable
public fun ComposeWindowOverlay(content: @Composable () -> Unit) {
    val overlays = LocalWindowOverlays.current
    if (overlays == null) {
        content()
        return
    }
    val context by rememberUpdatedState(currentCompositionLocalContext)
    val currentContent by rememberUpdatedState(content)
    val overlay: @Composable () -> Unit = remember {
        { CompositionLocalProvider(context) { currentContent() } }
    }
    DisposableEffect(overlays, overlay) {
        overlays.add(overlay)
        onDispose { overlays.remove(overlay) }
    }
}

private fun Modifier.hideWhenCovered(covered: Boolean): Modifier =
    if (covered) clearAndSetSemantics { hideFromAccessibility() } else this
