package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

/**
 * Action menu of the kit (HB-DS-019): a bounded list of actions with an identified anchor.
 *
 * The menu opens from its [trigger] in a focusable popup, keeps the documented keyboard contract
 * (arrows move the active action, Enter activates, Escape closes) and returns the focus to the
 * trigger after closing. Separators belong to the owner: pass an action with an empty
 * [HbMenuAction.onClick] only when the row is decorative; otherwise group actions in one menu.
 * [selected] marks a chosen option without turning the action into a checkbox.
 */
@Composable
fun HbMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<HbMenuAction>,
    modifier: Modifier = Modifier,
    trigger: @Composable () -> Unit,
) {
    val triggerRequester = remember { FocusRequester() }
    val gap = with(LocalDensity.current) { MENU_GAP.roundToPx() }

    fun close() {
        onDismissRequest()
        triggerRequester.requestFocus()
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .focusRequester(triggerRequester)
                .focusTarget(),
        ) {
            trigger()
        }

        if (expanded) {
            Popup(
                popupPositionProvider = remember(gap) { HbAnchoredPopupPositionProvider(gap = gap) },
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                HbMenuSurface(
                    actions = actions,
                    onDismiss = { close() },
                    onActivate = { action ->
                        action.onClick()
                        close()
                    },
                )
            }
        }
    }
}

/** One action of an [HbMenu]. */
data class HbMenuAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val shortcut: String? = null,
    val selected: Boolean? = null,
)

@Composable
private fun HbMenuSurface(
    actions: List<HbMenuAction>,
    onDismiss: () -> Unit,
    onActivate: (HbMenuAction) -> Unit,
) {
    val colors = HbTheme.colors
    val enabledIndices = remember(actions) { actions.indices.filter { actions[it].enabled } }
    var active by remember(actions) { mutableStateOf(enabledIndices.firstOrNull() ?: 0) }
    val requesters = remember(actions.size) { List(actions.size) { FocusRequester() } }

    key(active) {
        SideEffect {
            requesters.getOrNull(active)?.requestFocus()
        }
    }

    HbOptionSurface { maxHeight ->
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(HbSpacing.Xxs)
                .semantics { collectionInfo = CollectionInfo(rowCount = actions.size, columnCount = 1) }
                .onPreviewKeyEvent { event ->
                    menuKeyEvent(
                        event = event,
                        actions = actions,
                        active = active,
                        enabledIndices = enabledIndices,
                        onActiveChange = { active = it },
                        onActivate = onActivate,
                        onDismiss = onDismiss,
                    )
                },
        ) {
            actions.forEachIndexed { index, action ->
                MenuRow(
                    action = action,
                    active = index == active,
                    requester = requesters[index],
                    colors = colors,
                    onClick = { onActivate(action) },
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    action: HbMenuAction,
    active: Boolean,
    requester: FocusRequester,
    colors: HbColors,
    onClick: () -> Unit,
) {
    HbMenuItem(
        label = action.label,
        onClick = onClick,
        enabled = action.enabled,
        selected = action.selected,
        hint = action.shortcut,
        modifier = Modifier
            .focusRequester(requester)
            .then(
                if (active) {
                    Modifier.background(colors.surface.subtle)
                } else {
                    Modifier
                },
            ),
    )
}

private fun menuKeyEvent(
    event: KeyEvent,
    actions: List<HbMenuAction>,
    active: Int,
    enabledIndices: List<Int>,
    onActiveChange: (Int) -> Unit,
    onActivate: (HbMenuAction) -> Unit,
    onDismiss: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionDown -> {
            onActiveChange(nextEnabledIndex(enabledIndices, active, 1))
            true
        }

        Key.DirectionUp -> {
            onActiveChange(nextEnabledIndex(enabledIndices, active, -1))
            true
        }

        Key.Enter -> {
            val action = actions.getOrNull(active)?.takeIf { it.enabled }
            if (action != null) {
                onActivate(action)
                true
            } else {
                false
            }
        }

        Key.Escape -> {
            onDismiss()
            true
        }

        else -> false
    }
}

private fun nextEnabledIndex(indices: List<Int>, active: Int, step: Int): Int {
    if (indices.isEmpty()) return active
    val position = indices.indexOf(active).let { if (it < 0) 0 else it }
    return indices[(position + step).coerceIn(0, indices.lastIndex)]
}

private val MENU_GAP = 4.dp
