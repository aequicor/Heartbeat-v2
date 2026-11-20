package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import kotlinx.coroutines.flow.first

@Composable
internal fun AuroraMoreChats(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(AuroraLayout.ControlGap)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    Box(modifier.padding(start = AuroraLayout.NavigationMoreIndent, end = AuroraLayout.ContentInset)) {
        Row(
            Modifier.heightIn(min = AuroraLayout.NavigationRow).clip(shape)
                .then(if (hovered) Modifier.background(AuroraColors.NavigationHover) else Modifier)
                .then(
                    if (focused && HbFocusIndication.keyboardNavigation) {
                        Modifier.border(AuroraLayout.Stroke, AuroraColors.Primary, shape)
                    } else Modifier,
                )
                .hoverable(interaction)
                .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = AuroraLayout.Space, vertical = AuroraLayout.NavigationVerticalInset),
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                label,
                Modifier.weight(1f, fill = false),
                style = HbTypography.Label.copy(color = AuroraColors.DesktopInk),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SidebarChevron(true)
        }
    }
}

@Composable
internal fun RevealSidebarFocusTarget(list: LazyListState, rows: List<NavigationRow>, focusId: String?) {
    LaunchedEffect(focusId) {
        if (focusId == null) return@LaunchedEffect
        val index = rows.indexOfFirst { it.id == focusId }
        if (index >= 0 && list.layoutInfo.visibleItemsInfo.none { it.key == focusId }) list.scrollToItem(index)
    }
}

@Composable
internal fun sidebarTitleFocus(
    list: LazyListState,
    rowId: String,
    focusId: String?,
    onAction: (AiStudioAction) -> Unit,
): Modifier {
    if (rowId != focusId) return Modifier
    val requester = remember { FocusRequester() }
    val dispatch by rememberUpdatedState(onAction)
    LaunchedEffect(requester) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == rowId } }.first { it }
        if (requester.requestFocus()) dispatch(AiStudioAction.NavigationFocusHandled(rowId))
    }
    return Modifier.focusRequester(requester)
}
