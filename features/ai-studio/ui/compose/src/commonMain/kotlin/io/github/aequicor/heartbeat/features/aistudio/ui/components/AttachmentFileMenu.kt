package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_copy
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_save
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

internal val LocalAttachmentContextMenuOpened = compositionLocalOf<() -> Unit> { {} }

@Composable
internal fun AuroraAttachmentFileMenu(
    id: String,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null,
    content: @Composable (Modifier, () -> Unit) -> Unit,
) {
    if (touch && onAttach == null) {
        content(modifier) {}
        return
    }
    var expanded by remember(id) { mutableStateOf(false) }
    var anchor by remember(id) { mutableStateOf<IntOffset?>(null) }
    val onPointerOpen by rememberUpdatedState(LocalAttachmentContextMenuOpened.current)
    HbMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        actions = listOfNotNull(onAttach?.let { HbMenuAction("Прикрепить к сообщению", it) }) + attachmentFileActions(
            onCopy = { onAction(AiStudioAction.CopyFile(id)) },
            onSave = { onAction(AiStudioAction.SaveFile(id)) },
        ),
        modifier = modifier,
        presentation = HbMenuPresentation.AuroraFileActions,
        alignEnd = true,
        restoreTriggerFocus = anchor == null,
        pointerAnchor = anchor,
    ) { trigger ->
        val open = {
            anchor = null
            expanded = true
        }
        content(
            trigger.pointerInput(id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                        val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse && !it.isConsumed }
                            ?: continue
                        mouse.consume()
                        onPointerOpen()
                        anchor = IntOffset(mouse.position.x.roundToInt(), mouse.position.y.roundToInt())
                        expanded = true
                    }
                }
            }.onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Menu ||
                        event.key == Key.F10 && event.isShiftPressed)
                ) {
                    open()
                    true
                } else false
            },
            open,
        )
    }
}

internal fun attachmentFileActions(onCopy: () -> Unit, onSave: () -> Unit): List<HbMenuAction> = listOf(
    attachmentCopyAction(onCopy),
    HbMenuAction("Сохранить", onSave, leadingIcon = {
        Image(painterResource(Res.drawable.attachment_viewer_save), null, Modifier.size(18.dp))
    }),
)

internal fun attachmentCopyAction(onCopy: () -> Unit): HbMenuAction = HbMenuAction("Скопировать", onCopy, leadingIcon = {
    Image(painterResource(Res.drawable.attachment_viewer_copy), null, Modifier.size(18.dp))
})
