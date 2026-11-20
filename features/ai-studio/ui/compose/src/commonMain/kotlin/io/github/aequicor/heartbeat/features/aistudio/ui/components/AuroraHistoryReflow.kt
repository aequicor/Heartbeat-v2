package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.offset
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Anchors the activated row while a work disclosure changes the size of its reply. */
internal class AuroraHistoryReflow(
    private val list: LazyListState,
    private val currentViewportHeight: () -> Dp = { Dp.Hairline },
) : NestedScrollConnection {
    val viewportHeight: Dp get() = currentViewportHeight()
    private var reservedTail by mutableFloatStateOf(0f)
    private var request: Adjustment? = null
    private var motion: Resize? = null
    private var anchored: AnchoredResize? = null
    private val shortHistoryClearances = mutableStateMapOf<String, State<Int>>()

    // A top-aligned list that fits its viewport has no scroll offset to absorb the new clearance.
    val shortHistoryTopShift: Int get() = shortHistoryClearances.values.sumOf { it.value }

    fun beforeToggle(id: String, expanded: Boolean, displayedHeight: Int, animated: Boolean, clearance: State<Int>?) {
        val shortHistory = !list.canScrollForward && !list.canScrollBackward
        if (clearance != null && (shortHistory || id in shortHistoryClearances)) {
            shortHistoryClearances[id] = clearance
        }
        val closesOverlay = clearance != null && expanded
        val toTail = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            ?.let { (-it.offset).coerceAtLeast(0) } ?: displayedHeight
        anchored = if (closesOverlay) null else AnchoredResize(
            id, displayedHeight, reservedTail, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, toTail,
        )
        val target = if (closesOverlay) collapse(id, displayedHeight, toTail) else null
        request = target
        motion = target?.takeIf { animated }?.let {
            Resize(displayedHeight, reservedTail, list.firstVisibleItemScrollOffset)
        }
    }

    private fun collapse(id: String, workHeight: Int, toTail: Int): Adjustment {
        val scroll = minOf(toTail, workHeight)
        return Adjustment(
            id = id,
            expands = false,
            index = list.firstVisibleItemIndex,
            offset = list.firstVisibleItemScrollOffset - scroll,
            tail = reservedTail.roundToInt() + workHeight - scroll,
        )
    }

    fun ready(expanded: Set<String>): Adjustment? =
        request?.takeIf { motion == null && (it.id in expanded) == it.expands }

    fun tailPixels(adjustment: Adjustment?): Int = adjustment?.tail ?: reservedTail.roundToInt()

    fun apply(adjustment: Adjustment) {
        request = null
        motion = null
        reservedTail = adjustment.tail.toFloat()
        if (!adjustment.expands) list.requestScrollToItem(adjustment.index, adjustment.offset)
    }

    fun workResized(id: String, expanded: Boolean, shown: Int, targetHeight: Int) {
        val anchor = anchored?.takeIf { it.id == id }
        if (anchor != null) {
            updateAnchored(anchor, shown, targetHeight)
        } else if (!expanded) {
            updateClosing(id, shown, targetHeight)
        }
    }

    private fun updateClosing(id: String, shown: Int, targetHeight: Int) {
        val target = request?.takeIf { it.id == id } ?: return
        val resize = motion ?: return
        val distance = targetHeight - resize.height
        val progress = if (distance == 0) 1f else ((shown - resize.height).toFloat() / distance).coerceIn(0f, 1f)
        reservedTail = resize.tail + (target.tail - resize.tail) * progress
        val offset = resize.offset + ((target.offset - resize.offset) * progress).roundToInt()
        if (offset != resize.appliedOffset && !list.isScrollInProgress) {
            list.requestScrollToItem(target.index, offset)
            resize.appliedOffset = offset
        }
        if (progress == 1f) {
            request = null
            motion = null
        }
    }

    private fun updateAnchored(anchor: AnchoredResize, shown: Int, targetHeight: Int) {
        val growth = shown - anchor.height
        val tailChange = if (growth < 0) (-growth - anchor.toTail).coerceAtLeast(0).toFloat() else {
            -minOf(anchor.tail, growth.toFloat())
        }
        reservedTail = anchor.tail + tailChange
        val offset = anchor.offset + growth + tailChange.roundToInt()
        if (offset != anchor.appliedOffset && !list.isScrollInProgress) {
            list.requestScrollToItem(anchor.index, offset)
            anchor.appliedOffset = offset
        }
        if (targetHeight > 0 && shown == targetHeight) anchored = null
    }

    fun streamingReplyResized(id: String, delta: Int, scope: CoroutineScope, selected: Boolean) {
        if (delta == 0 || anchored?.id == id || !isReading(id, selected)) return
        if (list.isScrollInProgress) {
            // Resizing moves the origin of the reverse list. Apply only this displacement
            // after layout so the wheel or fling keeps its own scroll mutation and velocity.
            scope.launch {
                if (isReading(id, selected)) list.dispatchRawDelta(delta.toFloat())
            }
        } else {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset + delta)
        }
    }

    private fun isReading(id: String, selected: Boolean): Boolean = (selected || list.canScrollBackward) &&
        list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }?.key == id

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
            motion = null
            request = null
            anchored = null
        }
        if (source != NestedScrollSource.UserInput || available.y <= 0f || reservedTail <= 0f) return Offset.Zero
        // Shrinking the reverse list's bottom inset moves the rows down, in the direction of this gesture.
        val consumed = minOf(reservedTail, available.y)
        reservedTail -= consumed
        return Offset(0f, consumed)
    }

    data class Adjustment(val id: String, val expands: Boolean, val index: Int, val offset: Int, val tail: Int)
    private class Resize(val height: Int, val tail: Float, val offset: Int) {
        var appliedOffset = offset
    }

    private class AnchoredResize(
        val id: String,
        val height: Int,
        val tail: Float,
        val index: Int,
        val offset: Int,
        val toTail: Int,
    ) {
        var appliedOffset = offset
    }
}

internal fun PaddingValues.withAuroraReflowInsets(tail: () -> Dp, above: () -> Dp): PaddingValues =
    object : PaddingValues by this {
        override fun calculateBottomPadding(): Dp = this@withAuroraReflowInsets.calculateBottomPadding() + tail()
        override fun calculateTopPadding(): Dp =
            (this@withAuroraReflowInsets.calculateTopPadding() - above()).coerceAtLeast(Dp.Hairline)
    }

@Composable
internal fun rememberAuroraWorkHeaderClearance(headerHeight: Int, expanded: Boolean): State<Int> {
    val target = when {
        !expanded -> 0
        headerHeight > 0 -> headerHeight
        else -> with(LocalDensity.current) { AuroraLayout.DesktopWorkDisclosureTarget.roundToPx() }
    }
    return if (HbTheme.motion == HbMotionPreference.Reduced) {
        rememberUpdatedState(target)
    } else {
        animateIntAsState(target, tween(HbMotions.Default.ordinaryTransitionMs), label = "Work header clearance")
    }
}

internal fun Modifier.auroraWorkHeaderClearance(space: State<Int>): Modifier =
    layout { measurable, constraints ->
        val above = space.value.coerceAtMost(constraints.maxHeight)
        val reply = measurable.measure(constraints.offset(vertical = -above))
        layout(reply.width, reply.height + above) { reply.placeRelative(0, above) }
    }

@Composable
@ReadOnlyComposable
internal fun Modifier.auroraHeightMotion(
    durationMillis: Int,
    finalResize: Boolean,
    onResizeEnd: () -> Unit,
): Modifier =
    if (HbTheme.motion == HbMotionPreference.Reduced) this else animateContentSize(
        tween(if (finalResize) FinalResizeMillis else durationMillis),
        alignment = Alignment.TopStart,
        finishedListener = { _, _ -> onResizeEnd() },
    )

@Composable
internal fun auroraWorkHeightMotion(
    access: AuroraWorkAccess,
    finalResize: Boolean,
    onResizeEnd: () -> Unit,
    onFrame: (height: Int, target: Int) -> Unit,
): Modifier {
    val animated = HbTheme.motion != HbMotionPreference.Reduced
    val duration = if (finalResize) FinalResizeMillis else HbMotions.Default.ordinaryTransitionMs
    val resize by rememberUpdatedState(onFrame)
    val finish by rememberUpdatedState(onResizeEnd)
    var target by remember(access) { mutableIntStateOf(-1) }
    var shown by remember(access) { mutableIntStateOf(-1) }
    // Publish the height and its scroll compensation before LazyColumn measures this frame.
    if (target >= 0) {
        val end = target
        LaunchedEffect(end, animated, duration) {
            val start = access.displayedHistoryHeight
            if (animated && start != end) {
                Animatable(start, Int.VectorConverter).animateTo(end, tween(duration)) {
                    shown = value
                    resize(value, end)
                }
                finish()
            } else {
                shown = end
                resize(end, end)
            }
        }
    }
    return Modifier.clipToBounds().then(remember(access) {
        object : LayoutModifier {
            private var measuredHeight = -1

            // Width queries must not update the displayed height or start a scroll adjustment.
            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
                measurable.maxIntrinsicWidth(height)

            override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
                val work = measurable.measure(constraints)
                // Lazy rows enter the viewport at their final height. Start resize bookkeeping
                // only after that height actually changes, so entering a row avoids a second layout.
                if (measuredHeight >= 0 && work.height != measuredHeight) target = work.height
                measuredHeight = work.height
                val height = constraints.constrainHeight(
                    when {
                        shown >= 0 -> shown
                        target < 0 -> work.height
                        else -> access.displayedHistoryHeight
                    },
                )
                access.displayedHistoryHeight = height
                return layout(work.width, height) { work.placeRelative(0, 0) }
            }
        }
    })
}

private const val FinalResizeMillis = 360

internal fun Message.auroraGapBefore(older: Message?, touch: Boolean): Dp {
    if (touch || older == null) return Dp.Hairline
    val related = (this is User) == (older is User) && promptRoot == older.promptRoot
    return if (related) AuroraLayout.RelatedMessageGap else AuroraLayout.DesktopMessageGap
}

private val Message.promptRoot: String
    get() = when (this) {
        is Reply -> promptId ?: id
        is User -> promptId ?: id
    }
