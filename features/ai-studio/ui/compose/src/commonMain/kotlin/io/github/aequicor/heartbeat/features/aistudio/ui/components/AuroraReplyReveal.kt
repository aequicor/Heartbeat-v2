package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

@Suppress("CompositionLocalAllowlist", "NoNonPrivateGlobalVariables")
internal val LocalReplyRevealBlock = staticCompositionLocalOf { 0 }

@Suppress("CompositionLocalAllowlist")
private val LocalReplyReveal = staticCompositionLocalOf<ReplyRevealContext?> { null }

private class ReplyRevealContext(val reveal: ReplyReveal, val streaming: Boolean, val active: Boolean)

@Composable
@ReadOnlyComposable
internal fun replyRevealIsFinal(): Boolean = LocalReplyReveal.current?.streaming == false

/** Owned by the reply, not by a paragraph which can be recreated by plain/rich or staged layout. */
@Composable
internal fun AuroraReplyReveal(streaming: Boolean, content: @Composable () -> Unit) {
    val active = streaming && HbTheme.motion != HbMotionPreference.Reduced
    val reveal = remember { ReplyReveal() }
    LaunchedEffect(reveal, active) {
        if (active) {
            while (true) withFrameNanos(reveal::frame)
        }
    }
    CompositionLocalProvider(LocalReplyReveal provides ReplyRevealContext(reveal, streaming, active), content = content)
}

private const val NanosPerMillisecond = 1_000_000L
private const val InitialTextOpacity = .15f

private data class RevealKey(val block: Int, val part: Any)
private class RevealRange(val start: Int, val end: Int, var time: Long)
private class RevealRevision(
    val text: String, val ranges: List<RevealRange>, val appended: List<RevealRange> = listOf(),
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

    fun frame(now: Long) {
        val start = origin ?: now.also { origin = it }
        currentTime = (now - start) / NanosPerMillisecond
        time = currentTime
    }

    fun prepare(key: RevealKey, text: String, active: Boolean): RevealRevision {
        val previous = seen[key]
        val draft = drafts[key]?.takeIf { it.text == text }
        val reformatted = previous == null && seen[RevealKey(key.block, "body")]?.text?.contains(text) == true
        // Formatting can replace visible syntax or reparent text. Such revisions must never
        // fade old characters again; subsequent append-only revisions can reveal normally.
        val revision = when {
            !active -> RevealRevision(text, listOf())
            draft != null -> draft
            previous?.text == text -> previous
            reformatted -> RevealRevision(text, listOf())
            previous != null && !text.startsWith(previous.text) -> RevealRevision(text, listOf())
            else -> {
                val start = previous?.text?.length ?: 0
                val ranges = previous?.ranges.orEmpty().filter {
                    currentTime - it.time < HbMotions.Default.smallStateChangeMs
                }
                val appended = revealWords(text, start, currentTime)
                RevealRevision(text, ranges + appended, appended)
            }
        }
        drafts[key] = revision
        return revision
    }

    fun commit(key: RevealKey, revision: RevealRevision) {
        if (seen[key] === revision) return
        timeline.replace(seen[key]?.ranges.orEmpty(), revision, currentTime)
        seen[key] = revision
        drafts.remove(key)
    }
}

private class RevealTimeline {
    private val ranges = mutableListOf<RevealRange>()

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
        for (range in ranges.asReversed()) {
            characters += range.end - range.start
            if (characters > MaximumAnimatedCharacters) range.time = now - duration
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
) {
    val context = LocalReplyReveal.current
    val reveal = context?.reveal
    val key = RevealKey(LocalReplyRevealBlock.current, revealKey)
    val revision = reveal?.prepare(key, revealSource, context.active)
    if (revision != null) SideEffect { reveal.commit(key, revision) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val fade = if (revision == null || revision.ranges.isEmpty()) Modifier else Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val measured = layout.value
            if (context.active && measured != null && !revision.finished) {
                val now = reveal.time
                revision.finished = revision.ranges.all { now - it.time >= HbMotions.Default.smallStateChangeMs }
                for (range in revision.ranges) {
                    val progress = ((now - range.time).toFloat() / HbMotions.Default.smallStateChangeMs)
                        .coerceIn(0f, 1f)
                    val start = (range.start - revealOffset).coerceIn(0, text.length)
                    val end = (range.end - revealOffset).coerceIn(0, text.length)
                    val hidden = if (now < range.time) 1f else (1f - InitialTextOpacity) * (1f - progress)
                    if (start < end && hidden > 0f) drawPath(
                        measured.getPathForRange(start, end),
                        HbAuroraColors.ContentMask.copy(alpha = hidden),
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
        }
    BasicText(text, modifier.then(fade), style, softWrap = softWrap, maxLines = maxLines, overflow = overflow,
        onTextLayout = {
        layout.value = it
        onTextLayout(it)
    })
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
) {
    AuroraReplyText(
        remember(text) { AnnotatedString(text) }, modifier, style, onTextLayout, softWrap,
        revealKey, revealSource, revealOffset, maxLines, overflow,
    )
}
