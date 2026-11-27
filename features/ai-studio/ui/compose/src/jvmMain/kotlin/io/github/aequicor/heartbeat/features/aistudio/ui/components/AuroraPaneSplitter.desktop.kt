package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

private val HorizontalResizePointerIcon = PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))

internal actual fun Modifier.auroraHorizontalResizeCursor(): Modifier =
    pointerHoverIcon(HorizontalResizePointerIcon)

internal actual fun Modifier.auroraHeldResizeCursor(active: Boolean): Modifier =
    pointerHoverIcon(
        if (active) HorizontalResizePointerIcon else PointerIcon.Default,
        overrideDescendants = active,
    )
