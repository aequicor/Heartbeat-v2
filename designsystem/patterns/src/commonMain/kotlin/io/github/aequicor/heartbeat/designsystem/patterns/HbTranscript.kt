package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbBadge
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Transcript of a conversation (HB-DS-023).
 *
 * The transcript owns the follow contract: it follows the latest content only while [following] is
 * true, and a reader who scrolls back suspends the follow instead of being pulled down by streaming
 * updates. The suspended state exposes [jumpToLatestLabel] and, when [newContentLabel] is present,
 * a new-content indication. Message identity and the expanded state stay in the caller's model, so
 * every item keeps its stable key and the focused control is not recycled silently.
 */
@Composable
fun HbTranscript(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    jumpToLatestLabel: String,
    modifier: Modifier = Modifier,
    newContentLabel: String? = null,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val currentOnFollowingChange by rememberUpdatedState(onFollowingChange)

    LaunchedEffect(state, following) {
        if (!following) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.collect { count ->
            if (count > 0) {
                state.scrollToItem(count - 1)
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to state.canScrollForward }
            .distinctUntilChanged()
            .collect { (scrolling, canScrollForward) ->
                if (!scrolling && canScrollForward) {
                    currentOnFollowingChange(false)
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            content()
        }
        if (!following) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(HbSpacing.S),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (newContentLabel != null) {
                    HbBadge(label = newContentLabel, tone = HbStatusTone.Info)
                }
                HbButton(
                    text = jumpToLatestLabel,
                    onClick = { onFollowingChange(true) },
                )
            }
        }
    }
}
