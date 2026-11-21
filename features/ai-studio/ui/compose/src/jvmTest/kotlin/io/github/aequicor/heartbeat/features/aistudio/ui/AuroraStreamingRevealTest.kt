@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraReplyReveal
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraReplyText
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraRichBlocks
import io.github.aequicor.heartbeat.features.aistudio.ui.components.PreparedBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyInline
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuroraStreamingRevealTest {
    @Test
    fun rapidPhrasesDoNotBuildAnUnboundedAnimationQueue() {
        val clock = AuroraHoverTestClock()
        var text by mutableStateOf("AAAA")
        var streaming by mutableStateOf(true)
        val scene = ImageComposeScene(600, 300, coroutineContext = clock.context) {
            AuroraTestTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    AuroraReplyReveal(streaming) {
                        AuroraReplyText(text, style = TextStyle(
                            color = Color.Black, fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp, lineHeight = 24.sp,
                        ))
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            repeat(8) {
                text += "\n" + List(8) { "BBBB" }.joinToString(" ")
                clock.advance(scene, 16)
            }
            clock.advance(scene, 500)
            val settled = scene.image(clock).ink(0, 300)
            streaming = false
            clock.frame(scene)
            assertEquals(settled, scene.image(clock).ink(0, 300))
        } finally { scene.close() }
    }

    @Test
    fun aLargeBurstDoesNotLeaveAnEmptyViewportWhileItsTailCascades() {
        val clock = AuroraHoverTestClock()
        var streaming by mutableStateOf(true)
        var height = 0
        val text = List(24) { "AAAA BBBB CCCC DDDD EEEE FFFF GGGG HHHH" }.joinToString("\n")
        val scene = ImageComposeScene(600, 300, coroutineContext = clock.context) {
            AuroraTestTheme {
                Column(Modifier.fillMaxSize().background(Color.White)
                    .verticalScroll(rememberScrollState(Int.MAX_VALUE))) {
                    AuroraReplyReveal(streaming) {
                        AuroraReplyText(text, style = TextStyle(
                            color = Color.Black, fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp, lineHeight = 24.sp,
                        ), onTextLayout = { height = it.size.height })
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            val first = scene.image(clock)
            val fullHeight = height
            assertTrue(fullHeight > 300)
            assertTrue(first.ink(0, 216) > 0, "A received burst must not leave the viewport blank")
            clock.advance(scene, 32)
            val started = scene.image(clock)
            assertTrue(started.ink(276, 300) > 0, "Every word starts within one delivery window")
            streaming = false
            clock.frame(scene)
            val final = scene.image(clock)
            assertEquals(fullHeight, height)
            assertEquals(first.ink(0, 216), final.ink(0, 216), "The burst prefix appears immediately")
        } finally { scene.close() }
    }

    @Test
    fun answerCadencesKeepOldTextOpaqueAndDoNotLeaveARevealQueue() {
        for (portionsPerSecond in listOf(50, 150)) verifyAnswerCadence(portionsPerSecond)
    }

    private fun verifyAnswerCadence(portionsPerSecond: Int) {
        val clock = AuroraHoverTestClock()
        var text by mutableStateOf("AAAA")
        var streaming by mutableStateOf(true)
        val scene = ImageComposeScene(600, 300, coroutineContext = clock.context) {
            AuroraTestTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    AuroraReplyReveal(streaming) {
                        AuroraReplyText(text, style = TextStyle(
                            color = Color.Black, fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp, lineHeight = 24.sp,
                        ))
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            clock.advance(scene, 150)
            val original = scene.image(clock).ink(0, 24, 0, 48)
            repeat(30) { index ->
                text += " BBBB"
                val elapsed = (index + 1) * 1000L / portionsPerSecond - index * 1000L / portionsPerSecond
                clock.advance(scene, elapsed)
                // Growing layer bounds can change edge antialiasing by a few color levels.
                assertEquals(original.toDouble(), scene.image(clock).ink(0, 24, 0, 48).toDouble(),
                    original / 1000.0)
            }
            clock.advance(scene, 152)
            val settled = scene.image(clock).ink(0, 300)
            streaming = false
            clock.frame(scene)
            assertEquals(settled, scene.image(clock).ink(0, 300))
        } finally { scene.close() }
    }
    @Test
    fun aWholePhraseCascadesInReadingOrderAndCompletionBypassesTheQueue() {
        val clock = AuroraHoverTestClock()
        var streaming by mutableStateOf(true)
        var height = 0
        val scene = ImageComposeScene(360, 180, coroutineContext = clock.context) {
            AuroraTestTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    AuroraReplyReveal(streaming) {
                        AuroraReplyText("AAAA BBBB CCCC", style = TextStyle(
                            color = Color.Black, fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp, lineHeight = 24.sp,
                        ), onTextLayout = { height = it.size.height })
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            val first = scene.image(clock)
            val fullHeight = height
            assertTrue(fullHeight > 0)
            assertTrue(first.ink(0, 24, 0, 48) > 0)
            assertEquals(0L, first.ink(0, 24, 60, 108))
            assertEquals(0L, first.ink(0, 24, 120, 168))
            clock.advance(scene, 24)
            val overlapping = scene.image(clock)
            assertTrue(overlapping.ink(0, 24, 60, 108) > 0)
            assertEquals(0L, overlapping.ink(0, 24, 120, 168))
            streaming = false
            clock.frame(scene)
            val final = scene.image(clock)
            assertEquals(fullHeight, height)
            assertTrue(final.ink(0, 24, 0, 48) > overlapping.ink(0, 24, 0, 48))
            assertTrue(final.ink(0, 24, 60, 108) > overlapping.ink(0, 24, 60, 108))
            assertTrue(final.ink(0, 24, 120, 168) > 0)
            clock.advance(scene, 500)
            assertEquals(final.ink(0, 24), scene.image(clock).ink(0, 24))
        } finally { scene.close() }
    }

    @Test
    fun plainToRichKeepsThePreviouslyShownPrefixOpaque() {
        val clock = AuroraHoverTestClock()
        var rich by mutableStateOf(false)
        val style = TextStyle(
            color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 20.sp, lineHeight = 24.sp,
        )
        val block = PreparedBlock(
            ReplyBlock.Paragraph(0, listOf(listOf(ReplyInline("AAAA\nBBBB")))),
            text = AnnotatedString("AAAA\nBBBB"),
        )
        val scene = ImageComposeScene(360, 180, coroutineContext = clock.context) {
            AuroraTestTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    AuroraReplyReveal(streaming = true) {
                        SelectionContainer {
                            if (rich) {
                                AuroraRichBlocks(listOf(block), touch = false, streaming = true, bodyStyle = style)
                            } else AuroraReplyText("AAAA\n", style = style)
                        }
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            clock.advance(scene, 150)
            val old = scene.image(clock).ink(0, 24)
            rich = true
            clock.frame(scene)
            val changed = scene.image(clock)
            assertEquals(old, changed.ink(0, 24))
            val new = changed.ink(24, 48)
            clock.advance(scene, 120)
            assertTrue(scene.image(clock).ink(24, 48) > new)
        } finally { scene.close() }
    }

    @Test
    fun reducedMotionShowsTheCompleteTextOnTheFirstFrame() {
        val clock = AuroraHoverTestClock()
        var streaming by mutableStateOf(true)
        val scene = ImageComposeScene(360, 180, coroutineContext = clock.context) {
            AuroraTestTheme(motion = HbMotionPreference.Reduced) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    AuroraReplyReveal(streaming) { AuroraReplyText("Сразу видно весь текст") }
                }
            }
        }
        try {
            clock.frame(scene)
            val first = scene.image(clock).ink(0, 60)
            assertTrue(first > 0)
            streaming = false
            clock.frame(scene)
            assertEquals(first, scene.image(clock).ink(0, 60))
        } finally { scene.close() }
    }

    @Test
    fun onlyNewRangesFadeWithoutDelayingLayoutOrRestartingThePreviousRange() {
        val clock = AuroraHoverTestClock()
        var text by mutableStateOf("AAAA")
        var streaming by mutableStateOf(true)
        var height = 0
        val scene = ImageComposeScene(360, 180, coroutineContext = clock.context) {
            AuroraTestTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    AuroraReplyReveal(streaming) {
                        SelectionContainer {
                            AuroraReplyText(text, style = TextStyle(
                                color = Color.Black, fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp, lineHeight = 24.sp,
                            ), onTextLayout = { height = it.size.height })
                        }
                    }
                }
            }
        }
        try {
            clock.frame(scene)
            clock.advance(scene, 150)
            val original = scene.image(clock).ink(0, 24)
            assertTrue(original > 0)
            text += "\nBBBB"
            clock.frame(scene)
            assertEquals(48, height)
            val first = scene.image(clock)
            assertEquals(original, first.ink(0, 24))
            val faint = first.ink(24, 48)
            assertTrue(faint > 0)
            clock.advance(scene, 60)
            val growing = scene.image(clock).ink(24, 48)
            assertTrue(growing > faint)
            text += "\nCCCC"
            clock.frame(scene)
            val next = scene.image(clock)
            assertEquals(72, height)
            assertEquals(original, next.ink(0, 24))
            assertEquals(growing, next.ink(24, 48))
            assertTrue(next.ink(48, 72) < growing)
            streaming = false
            clock.frame(scene)
            val final = scene.image(clock)
            clock.advance(scene, 200)
            assertEquals(final.ink(0, 72), scene.image(clock).ink(0, 72))
            assertTrue(final.ink(48, 72) > next.ink(48, 72))
        } finally { scene.close() }
    }

    private fun ImageComposeScene.image(clock: AuroraHoverTestClock): BufferedImage = render(clock.nanoTime).use {
        val bytes = checkNotNull(it.encodeToData()).use { data -> data.bytes }
        ImageIO.read(ByteArrayInputStream(bytes))
    }

    private fun BufferedImage.ink(top: Int, bottom: Int, left: Int = 0, right: Int = width): Long {
        var ink = 0L
        for (y in top until bottom) for (x in left until right) ink += 255 - (getRGB(x, y) and 255)
        return ink
    }
}
