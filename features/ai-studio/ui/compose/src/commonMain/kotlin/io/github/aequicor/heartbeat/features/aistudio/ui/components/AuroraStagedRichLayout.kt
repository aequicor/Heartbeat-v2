package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.toSize
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import kotlinx.coroutines.flow.first
import kotlin.reflect.KClass
import kotlin.time.TimeSource

internal expect fun supportsAuroraStagedRichLayout(): Boolean

@Composable
internal fun AuroraPreparingReply(modifier: Modifier = Modifier, minHeight: Dp = Dp.Hairline) {
    if (minHeight > Dp.Hairline) {
        Box(modifier.heightIn(min = minHeight), contentAlignment = Alignment.BottomStart) {
            AuroraText(PreparingReplyText, small = true)
        }
    } else {
        AuroraText(PreparingReplyText, modifier, small = true)
    }
}

@Composable
internal fun AuroraDocumentBlocks(
    blocks: List<PreparedBlock>, touch: Boolean, streaming: Boolean, preparingHeight: Dp,
    archiveReveal: ArchiveReveal? = null,
    bodyStyle: TextStyle = auroraMessageStyle(),
) {
    val stageOpening = remember {
        supportsAuroraStagedRichLayout() &&
            (blocks.size >= StagedDocumentBlockCount || blocks.count { it.hasOwnViewport } >= StagedViewportCount)
    }
    if (!stageOpening) {
        val body = archiveReveal?.let { reveal -> Modifier.onPlaced { reveal.markPlaced() } } ?: Modifier
        AuroraRichBlocks(blocks, touch, streaming, modifier = body, bodyStyle = bodyStyle)
        return
    }

    val slots = remember(blocks) { OpeningGroups(blocks).ranges() }.map { range ->
        val group = blocks.subList(range.first, range.last + 1)
        val id = group.first().source.id
        val followsHeading = blocks.getOrNull(range.first - 1)?.source is ReplyBlock.Heading
        key(id) {
            remember(group, followsHeading, touch, streaming, bodyStyle) {
                val viewport = group.first().takeIf { it.hasOwnViewport }?.source?.let { it::class }
                AuroraRichLayoutSlot(id, viewport) {
                    AuroraRichBlocks(
                        group, touch, streaming,
                        modifier = CullRichGroupElement,
                        firstGroup = range.first == 0, followsHeading = followsHeading, bodyStyle = bodyStyle,
                    )
                }
            }
        }
    }
    AuroraStagedRichLayout(slots, preparingHeight, archiveReveal)
}

private class OpeningGroups(private val blocks: List<PreparedBlock>) {
    fun ranges(): List<IntRange> = buildList {
        var start = 0
        for (index in blocks.indices) {
            if (blocks[index].hasOwnViewport) {
                if (index > start) add(start until index)
                add(index..index)
                start = index + 1
            } else if (index - start + 1 == DocumentBlocksPerSlot) {
                add(start..index)
                start = index + 1
            }
        }
        if (start < blocks.size) add(start until blocks.size)
    }
}

private class AuroraRichLayoutSlot(
    val id: Int,
    val viewport: KClass<out ReplyBlock>?,
    val content: @Composable () -> Unit,
)

private data object CullRichGroupElement : ModifierNodeElement<CullRichGroupNode>() {
    override fun create() = CullRichGroupNode()
    override fun update(node: CullRichGroupNode) = Unit
}

private class CullRichGroupNode : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var drawn = false

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        // Moving a retained list layer may otherwise reuse the recording made while the group was clipped.
        if (drawn != coordinates.intersectsRoot()) invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawn = requireLayoutCoordinates().intersectsRoot()
        if (drawn) drawContent()
    }

    private fun LayoutCoordinates.intersectsRoot(): Boolean {
        val root = findRootCoordinates()
        val visible = root.localBoundingBoxOf(this, clipBounds = true)
        return visible.overlaps(Rect(Offset.Zero, root.size.toSize()))
    }
}

/** Opens the first large reply in stages while keeping one native selection container. */
@Composable
private fun AuroraStagedRichLayout(
    slots: List<AuroraRichLayoutSlot>,
    preparingHeight: Dp,
    archiveReveal: ArchiveReveal?,
) {
    val state = remember { SubcomposeLayoutState() }
    val latestSlots = rememberUpdatedState(slots)
    val latestArchiveReveal = rememberUpdatedState(archiveReveal)
    var measuredConstraints by remember { mutableStateOf<Constraints?>(null) }
    var staged by remember { mutableStateOf(listOf<AuroraRichLayoutSlot>()) }
    var ready by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    val constraints = measuredConstraints
    LaunchedEffect(constraints) {
        if (ready || constraints == null || constraints.hasBoundedHeight) return@LaunchedEffect
        try {
            state.prepareDocument(
                { latestSlots.value },
                constraints.copy(minWidth = 0, minHeight = 0),
                onPrepared = { staged = it },
            ) {
                ready = true
                snapshotFlow { placed }.first { it }
            }
        } finally {
            staged = listOf()
        }
    }
    // Keep the measure policy stable when a stream changes the final slice. Recomposition of
    // SubcomposeLayout itself would also invalidate the already composed preceding slices.
    val measure = remember<SubcomposeMeasureScope.(Constraints) -> MeasureResult> {
        { available: Constraints ->
            if (measuredConstraints != available) measuredConstraints = available
            if (ready || available.hasBoundedHeight) {
                measureDocument(latestSlots.value, available) {
                    if (ready && !placed) placed = true
                    latestArchiveReveal.value?.markPlaced()
                }
            } else {
                measureDocument(staged, available, concealed = true) {}
            }
        }
    }
    val visible = ready || constraints?.hasBoundedHeight == true
    val loading = if (visible) Modifier else Modifier.clipToBounds()
        .focusProperties { onEnter = { cancelFocusChange() } }
        .focusGroup()
        .clearAndSetSemantics { contentDescription = PreparingReplyText }
    Box(loading, propagateMinConstraints = true) {
        SubcomposeLayout(state, measurePolicy = measure)
        if (!visible) {
            AuroraPreparingReply(minHeight = preparingHeight)
            Spacer(Modifier.matchParentSize().blockStagedSelection())
        }
    }
}

private fun Modifier.blockStagedSelection(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { if (it.pressed || it.previousPressed) it.consume() }
        }
    }
}

private fun SubcomposeMeasureScope.measureDocument(
    slots: List<AuroraRichLayoutSlot>,
    available: Constraints,
    concealed: Boolean = false,
    afterPlacement: () -> Unit,
): MeasureResult {
    val loose = available.copy(minWidth = 0, minHeight = 0)
    var height = 0
    var width = 0
    val rows = slots.map { slot ->
        val remaining = if (loose.hasBoundedHeight) loose.copy(maxHeight = loose.maxHeight - height) else loose
        val placeable = subcompose(slot.id, slot.content).single().measure(remaining)
        height += placeable.height
        width = maxOf(width, placeable.width)
        placeable
    }
    val visibleWidth = if (concealed) available.minWidth else available.constrainWidth(width)
    val visibleHeight = if (concealed) available.minHeight else available.constrainHeight(height)
    return layout(visibleWidth, visibleHeight) {
        var top = 0
        val hiddenLeft = if (available.hasBoundedWidth) -available.maxWidth else -width
        for (row in rows) {
            if (concealed) row.place(hiddenLeft, top) else row.placeRelative(0, top)
            top += row.height
        }
        afterPlacement()
    }
}

private class PrecomposedRichSlot(
    val slot: AuroraRichLayoutSlot,
    val handle: SubcomposeLayoutState.PrecomposedSlotHandle,
    var measured: Boolean = false,
)

private class DocumentPreparation(private val layout: SubcomposeLayoutState, private val constraints: Constraints) {
    private val prepared = mutableMapOf<Int, PrecomposedRichSlot>()
    private val measuredViewports = mutableSetOf<KClass<out ReplyBlock>>()

    fun prepareFrame(slots: List<AuroraRichLayoutSlot>): Int {
        val budget = PreparationFrame()
        for (slot in slots) {
            if (!prepareSlot(slot, budget)) break
        }
        val firstIncomplete = slots.indexOfFirst { slot ->
            prepared[slot.id]?.let { it.slot === slot && it.measured } != true
        }
        return if (firstIncomplete < 0) slots.size else firstIncomplete
    }

    private fun prepareSlot(slot: AuroraRichLayoutSlot, budget: PreparationFrame): Boolean {
        val prior = prepared[slot.id]
        val current = prior?.takeIf { it.slot === slot }
        return when {
            current?.measured == true -> true
            budget.spent -> false
            slot.viewport != null && budget.viewportBudgetSpent -> true
            else -> {
                val pending = current ?: precompose(slot, prior, budget)
                measureSlot(pending, budget)
            }
        }
    }

    private fun precompose(
        slot: AuroraRichLayoutSlot, prior: PrecomposedRichSlot?, budget: PreparationFrame,
    ): PrecomposedRichSlot {
        prior?.handle?.dispose()
        budget.didWork = true
        return PrecomposedRichSlot(slot, layout.precompose(slot.id, slot.content)).also { prepared[slot.id] = it }
    }

    private fun measureSlot(pending: PrecomposedRichSlot, budget: PreparationFrame): Boolean {
        val viewport = pending.slot.viewport
        return when {
            // Leave the first measurement of each viewport type for the start of a batch.
            budget.didWork && viewport != null && viewport !in measuredViewports -> true
            budget.spent -> false
            viewport != null && budget.viewportBudgetSpent -> true
            else -> {
                if (pending.handle.placeablesCount == 1) pending.handle.premeasure(0, constraints)
                pending.measured = true
                if (viewport != null) measuredViewports += viewport
                budget.didWork = true
                true
            }
        }
    }

    fun dispose() {
        prepared.values.forEach { it.handle.dispose() }
    }
}

private class PreparationFrame {
    private val start = TimeSource.Monotonic.markNow()
    var didWork = false
    private val spentMs: Long get() = start.elapsedNow().inWholeMilliseconds
    val spent: Boolean get() = didWork && spentMs >= PreparationBudgetMs
    val viewportBudgetSpent: Boolean get() = didWork && spentMs >= ViewportBudgetMs
}

private suspend fun SubcomposeLayoutState.prepareDocument(
    latest: () -> List<AuroraRichLayoutSlot>,
    constraints: Constraints,
    onPrepared: (List<AuroraRichLayoutSlot>) -> Unit,
    publish: suspend () -> Unit,
) {
    val preparation = DocumentPreparation(this, constraints)
    try {
        var completed: List<AuroraRichLayoutSlot>? = null
        var activated = listOf<AuroraRichLayoutSlot>()
        while (completed !== latest()) {
            withFrameNanos {
                val slots = latest()
                val count = preparation.prepareFrame(slots)
                if (count == slots.size) {
                    completed = slots
                } else if (shouldActivate(activated, slots, count)) {
                    activated = slots.subList(0, count)
                    onPrepared(activated)
                }
            }
        }
        publish()
    } finally {
        preparation.dispose()
    }
}

private fun shouldActivate(
    activated: List<AuroraRichLayoutSlot>, slots: List<AuroraRichLayoutSlot>, readyCount: Int,
): Boolean = readyCount - activated.size >= ActivationBatchSize || readyCount < activated.size ||
    activated.indices.any { activated[it] !== slots[it] }

private const val PreparationBudgetMs = 8L
private const val ViewportBudgetMs = 6L
private const val ActivationBatchSize = 6
private const val StagedDocumentBlockCount = 64
private const val StagedViewportCount = 6
private const val DocumentBlocksPerSlot = 8
private const val PreparingReplyText = "Подготавливаю сообщение…"
