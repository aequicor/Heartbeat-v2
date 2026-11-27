package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight

internal class AnimatedHeightElement(
    private val height: AuroraMeasuredHeight,
    private val immediate: () -> Boolean,
    private val onImmediateResize: (Int) -> Unit,
) : ModifierNodeElement<AnimatedHeightElement.Node>() {

    var target by mutableIntStateOf(-1)
        private set
    private var frame = -1
    private var node: Node? = null

    fun setFrame(frame: Int) {
        if (this.frame == frame) return
        this.frame = frame
        // Mark the row for measurement before the reverse list applies the matching scroll offset.
        node?.invalidateMeasurement()
    }

    override fun create(): Node = Node(this)

    override fun update(node: Node) {
        node.bind(this)
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = height.hashCode()

    class Node(private var owner: AnimatedHeightElement) : Modifier.Node(), LayoutModifierNode {

        private var measured = -1

        fun bind(element: AnimatedHeightElement) {
            owner.node = null
            owner = element
            measured = -1
            if (isAttached) owner.node = this
        }

        override fun onAttach() {
            owner.node = this
        }

        override fun onDetach() {
            owner.node = null
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
            measurable.maxIntrinsicWidth(height)

        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val work = measurable.measure(constraints)
            val previous = measured
            measured = work.height
            if (previous >= 0 && measured != previous) owner.target = measured
            val immediate = owner.immediate()
            val displayed = constraints.constrainHeight(when {
                immediate || owner.target < 0 -> measured
                owner.frame >= 0 -> owner.frame
                else -> owner.height.displayed
            })
            owner.height.displayed = displayed
            if (immediate && previous >= 0 && previous != displayed) owner.onImmediateResize(displayed)
            return layout(work.width, displayed) { work.placeRelative(0, 0) }
        }
    }
}
