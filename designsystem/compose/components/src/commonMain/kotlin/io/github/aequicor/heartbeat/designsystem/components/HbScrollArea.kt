package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Scrolling viewport of the kit (HB-DS-009). The [state] belongs to the owner, so a rebuilt
 * composition keeps the position; a long content area scrolls inside its own bounds instead of
 * growing the surrounding layout.
 */
@Composable
fun HbScrollArea(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    horizontal: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.then(
            if (horizontal) {
                Modifier.horizontalScroll(state)
            } else {
                Modifier.verticalScroll(state)
            },
        ),
    ) {
        content()
    }
}
