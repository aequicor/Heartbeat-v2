package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbOrientation
import io.github.aequicor.heartbeat.designsystem.components.HbSplitter
import io.github.aequicor.heartbeat.designsystem.components.HbSplitterLabels
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.move_pane_splitter_left
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.move_pane_splitter_right
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.reset_pane_width
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraPaneSplitter(
    label: String,
    paneWidth: Dp,
    onResize: (delta: Dp, dragStartWidth: Dp?) -> Unit,
    onReset: () -> Unit,
    onPointerHeldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnResize by rememberUpdatedState(onResize)
    HbSplitter(
        orientation = HbOrientation.Vertical,
        onResize = { delta -> onResize(delta.dp, null) },
        onReset = onReset,
        labels = HbSplitterLabels(
            narrower = stringResource(Res.string.move_pane_splitter_left),
            wider = stringResource(Res.string.move_pane_splitter_right),
            reset = stringResource(Res.string.reset_pane_width),
        ),
        modifier = modifier.auroraResizeHandleCursor(onPointerHeldChange).semantics { contentDescription = label },
        showLine = false,
        onDragStart = { { displacement -> currentOnResize(displacement.dp, paneWidth) } },
    )
}

internal fun Modifier.auroraResizeHandleCursor(onPointerHeldChange: ((Boolean) -> Unit)?): Modifier {
    val hover = auroraHorizontalResizeCursor()
    if (onPointerHeldChange == null) return hover
    return hover.pointerInput(onPointerHeldChange) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            onPointerHeldChange(true)
            try {
                // Follow the press even after the moving handle no longer contains the pointer.
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.buttons.isPrimaryPressed && event.changes.any { it.id == down.id && it.pressed })
            } finally {
                onPointerHeldChange(false)
            }
        }
    }
}

internal expect fun Modifier.auroraHorizontalResizeCursor(): Modifier

internal expect fun Modifier.auroraHeldResizeCursor(active: Boolean): Modifier
