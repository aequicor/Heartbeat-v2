package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import kotlin.math.ceil
import kotlin.math.roundToInt

@Suppress("CompositionLocalAllowlist", "NoNonPrivateGlobalVariables")
internal val LocalAuroraExternalTimestamp = staticCompositionLocalOf<MutableState<Boolean>?> { null }

private val LocalTimestampParagraph = staticCompositionLocalOf<MessageTextGeometry?> { null }

private data class FooterCorrection(val estimated: Int, val actual: Int)

private val TimestampTailBaseline = HorizontalAlignmentLine(::maxOf)
private val TimestampTailTop = HorizontalAlignmentLine(::maxOf)
private val TimestampTailRight = VerticalAlignmentLine(::maxOf)
private val AttachmentInfoStart = VerticalAlignmentLine(::minOf)
private val AttachmentInfoEnd = VerticalAlignmentLine(::maxOf)
private val AttachmentInfoBottom = HorizontalAlignmentLine(::maxOf)

@Composable
internal fun AuroraMessageTimestamp(
    time: String,
    inlineAllowed: Boolean,
    textSetsWidth: Boolean,
    modifier: Modifier = Modifier,
    bottomPadding: Dp? = null,
    status: @Composable () -> Unit = {},
    content: @Composable ((TextLayoutResult) -> Unit) -> Unit,
) {
    val text = remember { MessageTextGeometry() }
    val paragraph = remember { MessageTextGeometry() }
    val correction = remember { mutableStateOf<FooterCorrection?>(null) }
    val externalTimestamp = LocalAuroraExternalTimestamp.current
    if (externalTimestamp != null) {
        DisposableEffect(externalTimestamp) {
            onDispose { externalTimestamp.value = false }
        }
    }
    Layout(
        contents = listOf(
            { CompositionLocalProvider(LocalTimestampParagraph provides paragraph) { Box { content(text::update) } } },
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
        val footerWidth = if (delivery != null && stamp != null) {
            delivery.width + AuroraLayout.Space.roundToPx() + stamp.width
        } else delivery?.width ?: 0
        val width = constraints.constrainWidth(maxOf(bodyWidth, footerWidth))
        val fittingStamp = stamp?.takeIf { it.width <= width }
        externalTimestamp?.value = stamp != null && fittingStamp == null
        placeMessageContent(
            width, body, delivery, fittingStamp, inlineAllowed, textLayout.takeIf { textSetsWidth },
            bottomPadding?.roundToPx(), paragraph.layout, correction,
        )
    }
}

private fun MeasureScope.placeMessageContent(
    width: Int,
    body: Placeable,
    delivery: Placeable?,
    stamp: Placeable?,
    inlineAllowed: Boolean,
    inlineText: TextLayoutResult?,
    bottomPadding: Int?,
    paragraph: TextLayoutResult?,
    correction: MutableState<FooterCorrection?>,
): MeasureResult {
    val gap = AuroraLayout.Space.roundToPx()
    val rowGap = AuroraLayout.SmallGap.roundToPx()
    val stampX = width - (stamp?.width ?: 0)
    // Inherited alignment lines can traverse the entire message, including nested lazy layouts.
    // Read the body's lines after placement to avoid Compose issue 549552303.
    // The final paragraph usually ends at the bottom of the body. Its measured text can
    // reserve the correct height on the first frame, before inherited offsets are available.
    val inlineGap = ceil(AuroraLayout.Space.toPx()).toInt()
    val estimatedInline = if (inlineAllowed && paragraph != null && stamp != null && delivery == null) {
        val baseline = body.height - paragraph.size.height + paragraph.lastBaseline.roundToInt()
        paragraph.timestampTail(baseline)?.stampTop(stamp, stampX, inlineGap, body.height) != null
    } else false
    val estimated = when {
        estimatedInline || stamp == null && delivery == null -> bottomPadding ?: gap
        else -> (bottomPadding ?: rowGap) + rowGap + maxOf(stamp?.height ?: 0, delivery?.height ?: 0)
    }
    val footer = correction.value?.takeIf { it.estimated == estimated }?.actual ?: estimated
    return layout(width, body.height + footer) {
        body.place(0, 0)
        val stampInline = if (stamp != null && inlineAllowed) {
            val tail = body.timestampTail() ?: inlineText?.timestampTail(body[LastBaseline])
            if (tail != null) tail.stampTop(stamp, stampX, inlineGap, body.height)
            else body.attachmentStampTop(stamp, stampX, inlineGap, rowGap)
        } else {
            null
        }
        val footerY = body.height + rowGap
        val splitFooter = delivery != null && delivery.width + gap > stampX
        val baselineShift = if (!splitFooter && stampInline == null && delivery != null && stamp != null) {
            val statusBaseline = delivery[FirstBaseline]
            val stampBaseline = stamp[FirstBaseline]
            if (statusBaseline == AlignmentLine.Unspecified || stampBaseline == AlignmentLine.Unspecified) 0
            else statusBaseline - stampBaseline
        } else 0
        val statusY = footerY + maxOf(0, -baselineShift)
        val stampY = stampInline ?: if (splitFooter) {
            statusY + delivery.height + rowGap
        } else {
            footerY + maxOf(0, baselineShift)
        }
        val footerBottom = maxOf(stamp?.let { stampY + it.height } ?: 0, delivery?.let { statusY + it.height } ?: 0)
        val bottom = bottomPadding ?: if (stampInline != null || footerBottom == 0) gap else rowGap
        val actual = maxOf(0, footerBottom - body.height) + bottom
        correction.value = if (actual == estimated) null else FooterCorrection(estimated, actual)
        delivery?.place(0, statusY)
        stamp?.place(stampX, stampY)
    }
}

internal fun Modifier.attachmentTimestampTail(): Modifier = layout { measurable, constraints ->
    val info = measurable.measure(constraints)
    val lines = mapOf(
        AttachmentInfoStart to 0,
        AttachmentInfoEnd to info.width,
        AttachmentInfoBottom to info.height,
    )
    layout(info.width, info.height, alignmentLines = lines) { info.place(0, 0) }
}

private fun Placeable.attachmentStampTop(stamp: Placeable, stampX: Int, gap: Int, rowGap: Int): Int? {
    val start = this[AttachmentInfoStart]
    if (start == AlignmentLine.Unspecified || stampX < start) return null
    val top = height - stamp.height
    val clearsInfo = top >= this[AttachmentInfoBottom] + rowGap || stampX >= this[AttachmentInfoEnd] + gap
    return top.takeIf { it >= 0 && clearsInfo }
}

@Composable
internal fun AuroraTimestampParagraph(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val geometry = remember { MessageTextGeometry() }
    val messageParagraph = LocalTimestampParagraph.current
    DisposableEffect(messageParagraph, geometry) {
        onDispose { messageParagraph?.clear(geometry.layout) }
    }
    val reportTail = Modifier.layout { measurable, constraints ->
        val paragraph = measurable.measure(constraints)
        val tail = geometry.layout?.let { it.timestampTail(it.lastBaseline.roundToInt()) }
        // Record the text coordinates so the footer sees the paragraph's position within the document.
        val lines = if (tail == null) emptyMap() else mapOf(
            TimestampTailBaseline to tail.baseline,
            TimestampTailTop to tail.top,
            TimestampTailRight to tail.right,
        )
        layout(paragraph.width, paragraph.height, alignmentLines = lines) {
            paragraph.place(0, 0)
        }
    }
    AuroraReplyText(text, modifier.then(reportTail), style, onTextLayout = { layout ->
        messageParagraph?.update(layout)
        geometry.update(layout)
        onTextLayout(layout)
    })
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

    fun clear(result: TextLayoutResult?) {
        if (layout === result) {
            layout = null
            occupied = null
        }
    }

    fun occupiedWidth(): Int = occupied ?: checkNotNull(layout).occupiedWidth().also { occupied = it }
}

private fun TextLayoutResult.occupiedWidth(): Int = ceil((0 until lineCount).maxOfOrNull(::getLineRight) ?: 0f).toInt()

private class TimestampTail(val baseline: Int, val top: Int, val right: Int) {

    fun stampTop(stamp: Placeable, stampX: Int, gap: Int, bodyHeight: Int): Int? {
        val stampBaseline = stamp[FirstBaseline]
        if (stampBaseline == AlignmentLine.Unspecified || stampBaseline != stamp[LastBaseline]) return null

        val stampTop = baseline - stampBaseline
        return stampTop.takeIf { right + gap <= stampX && it >= top && it + stamp.height <= bodyHeight }
    }
}

private fun Placeable.timestampTail(): TimestampTail? {
    val right = this[TimestampTailRight]
    if (right <= 0) return null
    return TimestampTail(this[TimestampTailBaseline], this[TimestampTailTop], right)
}

private fun TextLayoutResult.timestampTail(baseline: Int): TimestampTail? {
    val line = lineCount - 1
    if (line < 0 || baseline == AlignmentLine.Unspecified || !hasLeftToRightTail(line)) return null

    val top = baseline - (getLineBaseline(line) - getLineTop(line)).roundToInt()
    return TimestampTail(baseline, top, ceil(getLineRight(line)).toInt())
}

private fun TextLayoutResult.hasLeftToRightTail(line: Int): Boolean {
    val start = getLineStart(line)
    val end = getLineEnd(line, visibleEnd = true)
    return end > start && !layoutInput.text.endsWith('\n') &&
        getParagraphDirection(start) == ResolvedTextDirection.Ltr &&
        (start until end).all { getBidiRunDirection(it) == ResolvedTextDirection.Ltr }
}
