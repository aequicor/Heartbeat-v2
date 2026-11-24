package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import kotlin.math.ceil
import kotlin.math.roundToInt

private val ExternalTime = HorizontalAlignmentLine(::maxOf)

internal fun Placeable.needsExternalTimestamp(): Boolean = this[ExternalTime] != AlignmentLine.Unspecified

@Composable
internal fun AuroraMessageTimestamp(
    time: String,
    inlineAllowed: Boolean,
    textSetsWidth: Boolean,
    modifier: Modifier = Modifier,
    bottomPadding: Dp? = null,
    preferStatusWithTime: Boolean = false,
    status: @Composable () -> Unit = {},
    content: @Composable ((TextLayoutResult) -> Unit) -> Unit,
) {
    val text = remember { MessageTextGeometry() }
    Layout(
        contents = listOf(
            { Box { content(text::update) } },
            status,
            { if (time.isNotBlank()) AuroraTimestampText(time) },
        ),
        modifier = modifier,
    ) { (contentSlot, statusSlot, timeSlot), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val body = contentSlot.single().measure(loose)
        val delivery = statusSlot.singleOrNull()?.measure(loose)
        val textLayout = text.layout
        val bodyWidth = if (textSetsWidth && textLayout != null) text.occupiedWidth() else body.width
        val stamp = timeSlot.singleOrNull()?.measure(loose)
        val footerWidth = if (preferStatusWithTime && delivery != null && stamp != null) {
            delivery.width + AuroraLayout.Space.roundToPx() + stamp.width
        } else delivery?.width ?: 0
        val width = constraints.constrainWidth(maxOf(bodyWidth, footerWidth))
        val fittingStamp = stamp?.takeIf { it.width <= width }
        // The message row uses this marker to place a wide stamp on its own opaque surface.
        val alignment: Map<AlignmentLine, Int> = if (stamp != null && fittingStamp == null) {
            mapOf(ExternalTime to 0)
        } else {
            mapOf()
        }
        placeMessageContent(
            width, body, delivery, fittingStamp, textLayout.takeIf { inlineAllowed }, alignment,
            bottomPadding?.roundToPx(),
        )
    }
}

private fun MeasureScope.placeMessageContent(
    width: Int,
    body: Placeable,
    delivery: Placeable?,
    stamp: Placeable?,
    inlineText: TextLayoutResult?,
    alignment: Map<AlignmentLine, Int>,
    bottomPadding: Int?,
): MeasureResult {
    val gap = AuroraLayout.Space.roundToPx()
    val rowGap = AuroraLayout.SmallGap.roundToPx()
    val stampX = width - (stamp?.width ?: 0)
    val stampInline = if (stamp != null && inlineText != null) {
        body.timestampBaseline(stamp, inlineText, stampX, ceil(AuroraLayout.Space.toPx()).toInt())
    } else {
        null
    }
    val statusY = body.height + rowGap
    val stampY = stampInline ?: if (delivery == null || delivery.width + gap <= stampX) {
        statusY
    } else {
        statusY + delivery.height + rowGap
    }
    val footerBottom = maxOf(stamp?.let { stampY + it.height } ?: 0, delivery?.let { statusY + it.height } ?: 0)
    val bottom = bottomPadding ?: if (stampInline != null || footerBottom == 0) gap else rowGap
    val height = maxOf(body.height, footerBottom) + bottom
    return layout(width, height, alignmentLines = alignment) {
        body.place(0, 0)
        delivery?.place(0, statusY)
        stamp?.place(stampX, stampY)
    }
}

@Composable
internal fun AuroraTimestampText(time: String, modifier: Modifier = Modifier) {
    BasicText(
        time, modifier,
        softWrap = false,
        maxLines = 1,
        style = auroraTimestampStyle(),
    )
}

private class MessageTextGeometry {
    // Read during the parent's measure pass, after BasicText reports its layout.
    var layout: TextLayoutResult? = null
        private set
    private var occupied: Int? = null

    fun update(result: TextLayoutResult) {
        if (layout === result) return
        layout = result
        occupied = null
    }

    fun occupiedWidth(): Int = occupied ?: checkNotNull(layout).occupiedWidth().also { occupied = it }
}

private fun TextLayoutResult.occupiedWidth(): Int = ceil((0 until lineCount).maxOfOrNull(::getLineRight) ?: 0f).toInt()

private fun Placeable.timestampBaseline(
    stamp: Placeable,
    text: TextLayoutResult,
    stampX: Int,
    gap: Int,
): Int? {
    val line = text.lineCount - 1
    val baseline = this[LastBaseline]
    val stampBaseline = stamp[FirstBaseline]
    val hasTextBaseline = line >= 0 && baseline != AlignmentLine.Unspecified
    val hasSingleLineStamp = stampBaseline != AlignmentLine.Unspecified && stampBaseline == stamp[LastBaseline]
    if (!hasTextBaseline || !hasSingleLineStamp) return null

    val lineTop = baseline - (text.getLineBaseline(line) - text.getLineTop(line)).roundToInt()
    val stampTop = baseline - stampBaseline
    val fitsHorizontally = ceil(text.getLineRight(line)).toInt() + gap <= stampX
    val fitsVertically = stampTop >= lineTop && stampTop + stamp.height <= height
    return stampTop.takeIf { fitsHorizontally && fitsVertically && text.hasLeftToRightTail(line) }
}

private fun TextLayoutResult.hasLeftToRightTail(line: Int): Boolean {
    val start = getLineStart(line)
    val end = getLineEnd(line, visibleEnd = true)
    return end > start && !layoutInput.text.endsWith('\n') &&
        getParagraphDirection(start) == ResolvedTextDirection.Ltr &&
        (start until end).all { getBidiRunDirection(it) == ResolvedTextDirection.Ltr }
}
