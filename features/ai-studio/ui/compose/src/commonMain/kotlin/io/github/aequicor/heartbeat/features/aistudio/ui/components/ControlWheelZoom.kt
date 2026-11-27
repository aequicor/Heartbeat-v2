package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize
import kotlin.math.pow

internal const val ZoomStep = 1.25f

internal fun Modifier.controlWheelZoom(onZoom: (Float, Offset) -> Unit): Modifier =
    then(ControlWheelZoom(onZoom))

private data class ControlWheelZoom(val onZoom: (Float, Offset) -> Unit) : ModifierNodeElement<ControlWheelZoom.Node>() {

    override fun create() = Node(onZoom)

    override fun update(node: Node) {
        node.onZoom = onZoom
    }

    class Node(var onZoom: (Float, Offset) -> Unit) : Modifier.Node(), PointerInputModifierNode {

        override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
            if (pass != PointerEventPass.Initial || pointerEvent.type != PointerEventType.Scroll ||
                !pointerEvent.keyboardModifiers.isCtrlPressed
            ) return
            val change = pointerEvent.changes.firstOrNull { !it.isConsumed && it.scrollDelta.y != 0f } ?: return
            val anchor = change.position - Offset(bounds.width / 2f, bounds.height / 2f)
            onZoom(ZoomStep.pow(-change.scrollDelta.y), anchor)
            pointerEvent.changes.forEach { it.consume() }
        }

        override fun onCancelPointerInput() = Unit
    }
}
