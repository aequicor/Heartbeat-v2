package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing

/**
 * Lazy list of the kit (HB-DS-009): a bounded scrolling viewport with externally owned state and
 * stable item keys.
 *
 * The caller keeps the [state], so the scroll position survives a layout change; a stable [key]
 * keeps the identity of an item across filtering and streaming updates. The component owns only
 * spacing and the token gap, not the data or its loading.
 */
@Composable
fun <T> HbLazyList(
    items: List<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    key: ((item: T) -> Any)? = null,
    spacing: Dp = HbSpacing.Xs,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemContent: @Composable (item: T) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items(items = items, key = key) { item ->
            itemContent(item)
        }
    }
}
