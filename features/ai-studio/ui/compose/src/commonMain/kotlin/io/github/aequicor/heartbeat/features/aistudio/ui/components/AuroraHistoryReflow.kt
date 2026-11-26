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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Anchors the activated row while a work disclosure changes the size of its reply. */
internal class AuroraHistoryReflow(
    private val list: LazyListState,
    val tail: AuroraTailFollow = AuroraTailFollow(list),
    restoredAnchor: Anchor? = null,
    private val incomingReplyId: String? = null,
    private val currentViewportHeight: () -> Dp = { Dp.Hairline },
) : NestedScrollConnection {
    val viewportHeight: Dp get() = currentViewportHeight()
    private var reservedTail by mutableFloatStateOf(0f)
    private var anchored: AnchoredResize? = null
    private var openingAnchor = restoredAnchor?.takeIf { it.offset > 0 && it.messageId != null }
    private var incomingAnswerPlaced by mutableStateOf(false)
    private val shortHistoryClearances = mutableStateMapOf<String, State<Int>>()

    // A top-aligned list that fits its viewport has no scroll offset to absorb the new clearance.
    val shortHistoryTopShift: Int get() = shortHistoryClearances.values.sumOf { it.value }

    fun openingOffset(id: String): Int = openingAnchor?.takeIf { it.messageId == id }?.offset ?: 0

    fun isIncomingReply(id: String): Boolean = id == incomingReplyId

    fun isPreparingIncomingAnswer(id: String): Boolean = isIncomingReply(id) && !incomingAnswerPlaced

    fun finishOpening(id: String) {
        if (isIncomingReply(id)) incomingAnswerPlaced = true
        if (openingAnchor?.messageId == id) openingAnchor = null
    }

    fun beforeToggle(id: String, historyHeight: Int, answerHeight: Int, clearance: State<Int>?, scope: CoroutineScope) {
        tail.releaseReplyStart(id)
        val shortHistory = !list.canScrollForward && !list.canScrollBackward
        if (clearance != null && (shortHistory || id in shortHistoryClearances)) {
            shortHistoryClearances[id] = clearance
        }
        val toTail = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            ?.let { (-it.offset).coerceAtLeast(0) } ?: historyHeight
        val anchor = AnchoredResize(
            id, historyHeight, answerHeight, reservedTail, list.firstVisibleItemIndex,
            list.firstVisibleItemScrollOffset, toTail, tail.inputGeneration,
        )
        anchored = anchor
        scope.launch {
            // Some disclosures leave both sizes unchanged.
            delay(DisclosureStartWaitMillis)
            if (anchored === anchor && !anchor.resized) anchored = null
        }
    }

    fun tailPixels(): Int = reservedTail.roundToInt()

    fun workResized(id: String, shown: Int, target: Int, scope: CoroutineScope) {
        val anchor = anchorFor(id) ?: return
        anchor.history = shown
        anchor.historySettled = shown == target
        updateAnchored(anchor)
        releaseWhenSettled(anchor, scope)
    }

    fun answerResized(id: String, shown: Int, target: Int, scope: CoroutineScope) {
        val anchor = anchorFor(id) ?: return
        anchor.answer = shown
        anchor.answerSettled = shown == target
        updateAnchored(anchor)
        releaseWhenSettled(anchor, scope)
    }

    private fun updateAnchored(anchor: AnchoredResize) {
        anchor.resized = true
        val growth = anchor.history + anchor.answer - anchor.originalHeight
        val tailChange = if (growth < 0) (-growth - anchor.toTail).coerceAtLeast(0).toFloat() else {
            -minOf(anchor.tail, growth.toFloat())
        }
        reservedTail = anchor.tail + tailChange
        val offset = anchor.offset + growth + tailChange.roundToInt()
        if (offset != anchor.appliedOffset && !list.isScrollInProgress) {
            list.requestScrollToItem(anchor.index, offset)
            anchor.appliedOffset = offset
        }
    }

    private fun releaseWhenSettled(anchor: AnchoredResize, scope: CoroutineScope) {
        if (!anchor.settled || anchor.releasePending) return
        anchor.releasePending = true
        scope.launch {
            // Let both height animations publish the current frame before releasing its anchor.
            withFrameNanos {}
            anchor.releasePending = false
            if (anchored === anchor && anchor.settled) anchored = null
        }
    }

    private fun anchorFor(id: String): AnchoredResize? =
        anchored?.takeIf { it.id == id && it.input == tail.inputGeneration }

    fun ownsWorkResize(id: String): Boolean = anchorFor(id) != null

    fun streamingReplyResized(
        id: String, delta: Int, before: AuroraTailFollow.BeforeResize, scope: CoroutineScope, selected: Boolean,
    ) {
        if (delta == 0 || ownsWorkResize(id)) return
        tail.resized(id, delta, before, scope, selected)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
            tail.userScroll()
            anchored = null
            openingAnchor = null
        }
        if (source != NestedScrollSource.UserInput || available.y <= 0f || reservedTail <= 0f) return Offset.Zero
        // Shrinking the reverse list's bottom inset moves the rows down, in the direction of this gesture.
        val consumed = minOf(reservedTail, available.y)
        reservedTail -= consumed
        return Offset(0f, consumed)
    }

    private class AnchoredResize(
        val id: String,
        var history: Int,
        var answer: Int,
        val tail: Float,
        val index: Int,
        val offset: Int,
        val toTail: Int,
        val input: Int,
    ) {
        val originalHeight = history + answer
        var historySettled = false
        var answerSettled = true
        var resized = false
        var releasePending = false
        val settled get() = historySettled && answerSettled
        var appliedOffset = offset
    }
}

private const val DisclosureStartWaitMillis = 1000L

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
        else -> with(LocalDensity.current) { AuroraLayout.DesktopWorkHeaderHeight.roundToPx() }
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
    streaming: Boolean = false,
    onFrame: (height: Int, target: Int) -> Unit,
): Modifier = auroraMeasuredHeightMotion(
    access.historyHeight, if (finalResize) FinalResizeMillis else HbMotions.Default.ordinaryTransitionMs,
    onResizeEnd, streaming = streaming, onFrame = onFrame,
)

internal class AuroraMeasuredHeight {
    var displayed: Int = 0
}

@Composable
internal fun auroraMeasuredHeightMotion(
    height: AuroraMeasuredHeight,
    duration: Int,
    onResizeEnd: () -> Unit,
    streaming: Boolean = false,
    onFrame: (height: Int, target: Int) -> Unit,
): Modifier {
    val animated = HbTheme.motion != HbMotionPreference.Reduced && !streaming
    val resize by rememberUpdatedState(onFrame)
    val finish by rememberUpdatedState(onResizeEnd)
    val immediate by rememberUpdatedState(streaming)
    var target by remember(height) { mutableIntStateOf(-1) }
    var frame by remember(height) { mutableIntStateOf(-1) }
    if (target >= 0) {
        val end = target
        LaunchedEffect(end, animated, duration, streaming) {
            if (streaming) {
                frame = -1
                return@LaunchedEffect
            }
            val start = height.displayed
            if (animated && start != end) {
                Animatable(start, Int.VectorConverter).animateTo(end, tween(duration)) {
                    frame = value
                    resize(value, end)
                }
                finish()
            } else {
                frame = end
                resize(end, end)
            }
        }
    }
    return Modifier.clipToBounds().then(remember(height) {
        object : LayoutModifier {
            private var measuredHeight = -1

            // Width queries must not update the displayed height or start a scroll adjustment.
            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
                measurable.maxIntrinsicWidth(height)

            override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
                val work = measurable.measure(constraints)
                val previous = measuredHeight
                if (previous >= 0 && work.height != previous) target = work.height
                measuredHeight = work.height
                val displayed = constraints.constrainHeight(
                    when {
                        immediate || target < 0 -> work.height
                        frame >= 0 -> frame
                        else -> height.displayed
                    },
                )
                height.displayed = displayed
                if (immediate && previous >= 0 && previous != displayed) resize(displayed, displayed)
                return layout(work.width, displayed) { work.placeRelative(0, 0) }
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
