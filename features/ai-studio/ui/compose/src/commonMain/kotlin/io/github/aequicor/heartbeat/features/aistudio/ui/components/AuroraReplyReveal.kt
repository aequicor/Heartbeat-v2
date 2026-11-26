package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlinx.coroutines.flow.first
import kotlin.math.ceil
import kotlin.math.floor

@Suppress("CompositionLocalAllowlist", "NoNonPrivateGlobalVariables")
internal val LocalReplyRevealBlock = staticCompositionLocalOf { 0 }

@Suppress("CompositionLocalAllowlist", "NoNonPrivateGlobalVariables")
internal val LocalAuroraReplyArrival = staticCompositionLocalOf<ReplyArrivalPaint?> { null }

@Composable
internal fun Modifier.auroraReplySurfaceArrival(touch: Boolean): Modifier {
    val arrival = LocalAuroraReplyArrival.current ?: return this
    val origin = remember { mutableStateOf(Offset.Unspecified) }
    return onGloballyPositioned { origin.value = it.positionInRoot() }.drawWithCache {
        val mask = Path()
        val radius = AuroraLayout.MessageRadius.toPx()
        val side = (if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageHorizontalInset).toPx()
        val bottom = (if (touch) AuroraLayout.ContentInset else AuroraLayout.DesktopMessageVerticalInset).toPx()
        val stroke = AuroraLayout.Stroke.toPx()
        onDrawWithContent {
            val front = arrival.front()
            if (!front.isFinite()) {
                drawContent()
                return@onDrawWithContent
            }
            val position = origin.value
            if (position == Offset.Unspecified) return@onDrawWithContent
            val extent = arrival.visibleExtent(front) ?: return@onDrawWithContent
            val left = if (layoutDirection == LayoutDirection.Rtl) {
                (extent.left - position.x - side).coerceIn(0f, (size.width - 2 * radius).coerceAtLeast(0f))
            } else 0f
            val right = if (layoutDirection == LayoutDirection.Ltr) {
                (extent.right - position.x + side).coerceAtLeast(2 * radius).coerceAtMost(size.width)
            } else size.width
            val height = (extent.bottom - position.y + bottom).coerceAtLeast(2 * radius).coerceAtMost(size.height)
            val width = right - left
            if (height == size.height && width == size.width) drawContent()
            else if (width > stroke && height > stroke) {
                // Keep the reverse list's anchor while drawing only the area occupied by the reveal.
                mask.reset()
                mask.addRoundRect(RoundRect(Rect(left, 0f, right, height), CornerRadius(radius)))
                clipPath(mask) {
                    this@onDrawWithContent.drawContent()
                    drawRoundRect(
                        HbAuroraColors.MessageOutline, Offset(left + stroke / 2, stroke / 2),
                        Size(width - stroke, height - stroke), CornerRadius(radius - stroke / 2), style = Stroke(stroke),
                    )
                }
            }
        }
    }
}

internal class ReplyArrivalPaint(val isActive: () -> Boolean = { true }, val front: () -> Float) {
    private val edges = mutableStateListOf<(Float) -> Rect?>()

    fun attach(edge: (Float) -> Rect?) { edges.add(edge) }
    fun detach(edge: (Float) -> Rect?) { edges.remove(edge) }

    fun visibleExtent(front: Float): Rect? = edges.fold(null as Rect?) { extent, edge ->
        val next = edge(front)
        when {
            next == null -> extent
            extent == null -> next
            else -> Rect(
                minOf(extent.left, next.left), minOf(extent.top, next.top),
                maxOf(extent.right, next.right), maxOf(extent.bottom, next.bottom),
            )
        }
    }
}

@Composable
private fun RegisterReplyArrivalEdge(arrival: ReplyArrivalPaint, edge: (Float) -> Rect?) {
    DisposableEffect(arrival, edge) {
        arrival.attach(edge)
        onDispose { arrival.detach(edge) }
    }
}

/** A rule, table or source block needs its measured width as soon as it enters the reveal. */
@Composable
internal fun AuroraReplyArrivalBlock(content: @Composable () -> Unit) {
    if (LocalAuroraReplyArrival.current == null) {
        content()
        return
    }
    Box(Modifier.trackReplyArrivalBlock()) { content() }
}

@Composable
internal fun Modifier.trackReplyArrivalBlock(): Modifier {
    val arrival = LocalAuroraReplyArrival.current ?: return this
    val bounds = remember { mutableStateOf<Rect?>(null) }
    RegisterReplyArrivalEdge(arrival, remember(bounds) {
        { front -> bounds.value?.takeIf { !it.isEmpty && front > it.top }?.let { it.copy(bottom = minOf(front, it.bottom)) } }
    })
    return onGloballyPositioned {
        val position = it.positionInRoot()
        bounds.value = Rect(position, Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }
}

@Suppress("CompositionLocalAllowlist")
private val LocalReplyReveal = staticCompositionLocalOf<ReplyRevealContext?> { null }

private class ReplyRevealContext(
    val reveal: ReplyReveal,
    val streaming: Boolean,
    val active: Boolean,
    val work: Boolean = false,
    val animateInitial: Boolean = true,
)

@Composable
@ReadOnlyComposable
internal fun workTextRevealEnabled(): Boolean = LocalReplyReveal.current?.work == true

@Composable
@ReadOnlyComposable
internal fun replyTextRevealProvided(): Boolean = LocalReplyReveal.current != null

@Composable
internal fun AuroraWorkReveal(
    animateInitial: Boolean,
    ready: Boolean = true,
    streaming: Boolean = false,
    content: @Composable () -> Unit,
) {
    val active = HbTheme.motion != HbMotionPreference.Reduced
    val reveal = remember { ReplyReveal() }
    val animate = remember { animateInitial }
    SideEffect { reveal.composed = true }
    AuroraRevealClock(reveal, active && ready, continuous = false)
    val context = remember(reveal, streaming, active, animate) {
        ReplyRevealContext(reveal, streaming, active, work = true, animateInitial = animate)
    }
    CompositionLocalProvider(LocalReplyReveal provides context, content = content)
}

@Composable
@ReadOnlyComposable
internal fun replyRevealIsFinal(): Boolean = LocalReplyReveal.current?.streaming == false

/** Owned by the reply, not by a paragraph which can be recreated by plain/rich or staged layout. */
@Composable
internal fun AuroraReplyReveal(
    streaming: Boolean,
    revealFinalAdditions: Boolean = false,
    content: @Composable () -> Unit,
) {
    var startedLive by remember { mutableStateOf(streaming) }
    if (streaming && !startedLive) SideEffect { startedLive = true }
    val active = (streaming || startedLive && revealFinalAdditions) && HbTheme.motion != HbMotionPreference.Reduced
    val reveal = remember { ReplyReveal() }
    AuroraRevealClock(reveal, active, streaming)
    val context = remember(reveal, streaming, active) { ReplyRevealContext(reveal, streaming, active) }
    CompositionLocalProvider(LocalReplyReveal provides context, content = content)
}

@Composable
private fun AuroraRevealClock(reveal: ReplyReveal, active: Boolean, continuous: Boolean) {
    LaunchedEffect(reveal, active, continuous) {
        if (!active) return@LaunchedEffect
        if (continuous) {
            while (true) withFrameNanos(reveal::frame)
        } else {
            while (true) {
                snapshotFlow { reveal.animating }.first { it }
                reveal.resumeClock()
                while (reveal.animating) withFrameNanos(reveal::frame)
            }
        }
    }
}

private const val NanosPerMillisecond = 1_000_000L
private const val InitialTextOpacity = .15f

private data class RevealKey(val block: Int, val part: Any, val cascade: Boolean = true)
private class RevealRange(val start: Int, val end: Int, var time: Long)
private class RevealMask(val range: RevealRange, val path: Path)
private class RevealRevision(
    val text: String,
    val ranges: List<RevealRange>,
    val appended: List<RevealRange> = listOf(),
    val final: Boolean = false,
) {
    var finished = false
}

private class ReplyReveal {
    var time by mutableLongStateOf(0L)
    private var currentTime = 0L
    private var origin: Long? = null
    private val seen = mutableMapOf<RevealKey, RevealRevision>()
    private val drafts = mutableMapOf<RevealKey, RevealRevision>()
    private val timeline = RevealTimeline()
    private var labelDeadline = 0L
    var composed = false
    var animating by mutableStateOf(false)
        private set

    fun resumeClock() {
        origin = null
    }

    fun frame(now: Long) {
        val start = origin ?: (now - currentTime * NanosPerMillisecond).also { origin = it }
        currentTime = (now - start) / NanosPerMillisecond
        time = currentTime
        animating = currentTime < labelDeadline || timeline.hasPending(currentTime)
    }

    fun prepare(key: RevealKey, text: String, context: ReplyRevealContext): RevealRevision {
        val previous = seen[key]
        val finalized = !context.work && !context.streaming
        val draft = drafts[key]?.takeIf { it.text == text && it.final == finalized }
        val completing = finalized && previous?.final == false
        val reformatted = previous == null && seen[RevealKey(key.block, "body")]?.text?.contains(text) == true
        // Formatting can replace visible syntax or reparent text. Such revisions must never
        // fade old characters again; subsequent append-only revisions can reveal normally.
        val revision = when {
            !context.active -> RevealRevision(text, listOf(), final = finalized)
            draft != null -> draft
            previous?.text == text && !completing -> previous
            reformatted -> RevealRevision(text, listOf(), final = finalized)
            previous == null && !context.animateInitial && !composed -> RevealRevision(text, listOf(), final = finalized)
            !context.work && previous != null && !text.startsWith(previous.text) ->
                RevealRevision(text, listOf(), final = finalized)
            else -> {
                val start = previous?.text?.commonPrefixWith(text)?.length ?: 0
                val ranges = previous?.ranges.orEmpty().filter {
                    !completing && it.end <= start && currentTime - it.time < HbMotions.Default.smallStateChangeMs
                }
                val appended = if (start < text.length && (!key.cascade || context.work && text.length - start > MaximumAnimatedCharacters)) {
                    listOf(RevealRange(start, text.length, currentTime))
                } else revealWords(text, start, currentTime)
                RevealRevision(text, ranges + appended, appended, final = finalized)
            }
        }
        drafts[key] = revision
        return revision
    }

    fun commit(key: RevealKey, revision: RevealRevision) {
        if (seen[key] === revision) return
        if (key.cascade) {
            timeline.replace(seen[key]?.ranges.orEmpty(), revision, currentTime)
        } else if (revision.appended.isNotEmpty()) {
            labelDeadline = currentTime + HbMotions.Default.smallStateChangeMs
        }
        animating = currentTime < labelDeadline || timeline.hasPending(currentTime)
        seen[key] = revision
        drafts.remove(key)
    }
}

private class RevealTimeline {
    private val ranges = mutableListOf<RevealRange>()

    fun hasPending(now: Long): Boolean =
        ranges.any { now - it.time < HbMotions.Default.smallStateChangeMs }

    fun replace(previous: List<RevealRange>, revision: RevealRevision, now: Long) {
        val removed = previous.toSet() - revision.ranges.toSet()
        val duration = HbMotions.Default.smallStateChangeMs
        ranges.removeAll { it in removed || now - it.time >= duration }
        val step = duration - duration / CascadeOverlapDivisor
        for (range in revision.appended) {
            range.time = maxOf(now, ranges.lastOrNull()?.time?.plus(step) ?: now)
            ranges += range
        }
        finishOlderRanges(now)
        compressPending(now)
    }

    private fun finishOlderRanges(now: Long) {
        var characters = 0
        val duration = HbMotions.Default.smallStateChangeMs
        for ((index, range) in ranges.asReversed().withIndex()) {
            characters += range.end - range.start
            if (index > 0 && characters > MaximumAnimatedCharacters) range.time = now - duration
        }
        ranges.removeAll { now - it.time >= duration }
    }

    private fun compressPending(now: Long) {
        val maximum = MaximumCascadeDelayMs
        if ((ranges.lastOrNull()?.time ?: now) <= now + maximum) return
        // A burst must not create an ever-growing animation queue. Advance only segments
        // which have not started; never reduce the opacity of anything already revealed.
        val pending = ranges.filter { it.time > now }
        pending.forEachIndexed { index, range -> range.time = now + (index + 1) * maximum / pending.size }
    }
}

private const val CascadeOverlapDivisor = 4
private const val MaximumAnimatedCharacters = 80
private const val MaximumCascadeDelayMs = 32L

private fun revealWords(text: String, appendedStart: Int, now: Long): List<RevealRange> {
    // Full layout is immediate. Catch up a large burst without masking a viewport of text.
    val tail = (text.length - MaximumAnimatedCharacters).coerceAtLeast(appendedStart)
    val start = if (tail == appendedStart) tail else {
        (tail until text.length).firstOrNull { text[it].isWhitespace() }?.plus(1) ?: text.length
    }
    if ((start until text.length).all { text[it].isWhitespace() }) return listOf()
    val boundaries = mutableListOf(start)
    var hasWord = !text[start].isWhitespace()
    for (index in start + 1 until text.length) {
        if (!text[index].isWhitespace()) {
            if (hasWord && text[index - 1].isWhitespace()) boundaries += index
            hasWord = true
        }
    }
    return boundaries.mapIndexed { index, from ->
        RevealRange(from, boundaries.getOrNull(index + 1) ?: text.length, now)
    }
}

@Composable
internal fun AuroraReplyText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = auroraMessageStyle(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    softWrap: Boolean = true,
    revealKey: Any = "body",
    revealSource: String = text.text,
    revealOffset: Int = 0,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    cascade: Boolean = true,
) {
    val context = LocalReplyReveal.current
    val reveal = context?.reveal
    val key = RevealKey(LocalReplyRevealBlock.current, revealKey, cascade)
    val revision = reveal?.prepare(key, revealSource, context)
    if (revision != null) SideEffect { reveal.commit(key, revision) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val paint = LocalAuroraReplyArrival.current
    val arrival = if (paint == null) Modifier else {
        val origin = remember { mutableStateOf(Offset.Unspecified) }
        RegisterReplyArrivalEdge(paint, remember(layout, origin) {
            { front ->
                val position = origin.value
                if (position == Offset.Unspecified) null
                else layout.value?.revealedExtent(front - position.y)?.translate(position)
            }
        })
        Modifier.onGloballyPositioned { origin.value = it.positionInRoot() }
            .drawArrivalGlyphs(layout, paint.front) { origin.value.y }
    }
    val fade = if (revision == null || revision.ranges.isEmpty()) Modifier
    else Modifier.drawReplyReveal(reveal, revision, layout, revealOffset, text.length)
    val code = if (text.hasStringAnnotations(InlineCodeAnnotation, 0, text.length)) {
        Modifier.drawInlineCode(layout)
    } else Modifier
    BasicText(text, modifier.then(arrival).then(fade).then(code), style, softWrap = softWrap, maxLines = maxLines, overflow = overflow,
        onTextLayout = {
        layout.value = it
        onTextLayout(it)
    })
}

private fun Modifier.drawInlineCode(layout: State<TextLayoutResult?>): Modifier = drawWithCache {
    val measured = layout.value
    val paths = if (measured == null) emptyList() else {
        val text = measured.layoutInput.text
        text.getStringAnnotations(InlineCodeAnnotation, 0, text.length).map { span ->
            measured.getPathForRange(span.start, span.end)
        }
    }
    // BasicText paints selection before its glyphs. Paint code backgrounds before both.
    onDrawBehind {
        clipRect {
            for (path in paths) drawPath(path, HbAuroraColors.WorkHover)
        }
    }
}

private fun Modifier.drawArrivalGlyphs(
    layout: State<TextLayoutResult?>,
    glyphFront: () -> Float,
    top: () -> Float,
): Modifier = drawWithCache {
    val measured = layout.value
    val prefix = Path()
    onDrawWithContent {
        val revealed = glyphFront() - top()
        if (measured == null || revealed >= measured.size.height) {
            drawContent()
        } else if (revealed > 0f && measured.lineCount > 0) {
            val line = measured.getLineForVerticalPosition(revealed)
            val lineTop = measured.getLineTop(line)
            val start = measured.getLineStart(line)
            val cursor = measured.arrivalCursor(line, revealed)
            prefix.reset()
            if (line > 0) prefix.addRect(Rect(0f, 0f, size.width, lineTop))
            if (cursor > start) prefix.addPath(measured.getPathForRange(start, cursor))
            clipPath(prefix) { this@onDrawWithContent.drawContent() }
        }
    }
}

private fun TextLayoutResult.arrivalCursor(line: Int, front: Float): Int {
    val start = getLineStart(line)
    val end = getLineEnd(line, visibleEnd = true)
    val top = getLineTop(line)
    val fraction = ((front - top) / (getLineBottom(line) - top)).coerceIn(0f, 1f)
    val cursor = start + ((end - start) * fraction).toInt()
    return if (cursor > start && layoutInput.text[cursor - 1].isHighSurrogate()) cursor - 1 else cursor
}

private fun TextLayoutResult.revealedExtent(front: Float): Rect? {
    if (front <= 0f || lineCount == 0) return null
    val line = getLineForVerticalPosition(front)
    val start = getLineStart(line)
    val cursor = arrivalCursor(line, front)
    val partial = if (cursor > start) getPathForRange(start, cursor).getBounds().takeUnless { it.isEmpty } else null
    var left = partial?.left ?: Float.POSITIVE_INFINITY
    var right = partial?.right ?: Float.NEGATIVE_INFINITY
    var bottom = if (partial != null) getLineBottom(line) else 0f
    for (index in 0 until line) {
        if (getLineEnd(index, visibleEnd = true) == getLineStart(index)) continue
        left = minOf(left, getLineLeft(index))
        right = maxOf(right, getLineRight(index))
        bottom = maxOf(bottom, getLineBottom(index))
    }
    return if (right > left) Rect(left, getLineTop(0), right, bottom) else null
}

private fun Modifier.drawReplyReveal(
    reveal: ReplyReveal,
    revision: RevealRevision,
    layout: State<TextLayoutResult?>,
    offset: Int,
    length: Int,
): Modifier = drawWithCache {
    val measured = layout.value
    val masks = if (measured == null) emptyList() else revision.ranges.mapNotNull { range ->
        val start = (range.start - offset).coerceIn(0, length)
        val end = (range.end - offset).coerceIn(0, length)
        if (start >= end) null else RevealMask(range, measured.getPathForRange(start, end)).takeUnless { it.path.isEmpty }
    }
    val bounds = masks.takeIf { it.isNotEmpty() }?.let {
        val rows = masks.map { mask -> mask.path.getBounds() }
        Rect(0f, floor(rows.minOf { row -> row.top }), size.width, ceil(rows.maxOf { row -> row.bottom }))
    }
    val layer = Paint()
    val eraser = Paint().apply {
        color = HbAuroraColors.ContentMask
        blendMode = BlendMode.DstOut
        isAntiAlias = false
    }
    val duration = HbMotions.Default.smallStateChangeMs
    onDrawWithContent {
        if (bounds == null || revision.finished) {
            drawContent()
            return@onDrawWithContent
        }
        val now = reveal.time
        revision.finished = revision.ranges.all { now - it.time >= duration }
        if (revision.finished) {
            drawContent()
            return@onDrawWithContent
        }
        // Keep the already visible lines on the native text canvas.
        drawIntoCanvas { canvas ->
            canvas.withSave {
                canvas.clipRect(bounds, ClipOp.Difference)
                drawContent()
            }
            canvas.withSave {
                canvas.clipRect(bounds)
                canvas.withSaveLayer(bounds, layer) {
                    drawContent()
                    for (mask in masks) {
                        val elapsed = now - mask.range.time
                        if (elapsed >= duration) continue
                        eraser.alpha = if (elapsed < 0) 1f else (1f - InitialTextOpacity) * (1f - elapsed.toFloat() / duration)
                        canvas.drawPath(mask.path, eraser)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuroraReplyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = auroraMessageStyle(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    softWrap: Boolean = true,
    revealKey: Any = "body",
    revealSource: String = text,
    revealOffset: Int = 0,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    cascade: Boolean = true,
) {
    AuroraReplyText(
        remember(text) { AnnotatedString(text) }, modifier, style, onTextLayout, softWrap,
        revealKey, revealSource, revealOffset, maxLines, overflow, cascade,
    )
}
