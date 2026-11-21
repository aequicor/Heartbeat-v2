@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Step
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraReplyAnswer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuroraReplyEmptySpaceTest {
    @Test
    fun thinkingReservesNoEmptyAnswerAndTheFirstAnswerGetsOnlyItsNormalInsets() {
        for (touch in listOf(false, true)) {
            val clock = AuroraHoverTestClock()
            var reply by mutableStateOf(Reply("stream", "", steps = listOf(
                Step("thinking", "Размышления", "", StepStatus.Current),
            )))
            val stream = MutableStateFlow(LiveText(reply.id, ""))
            var streaming by mutableStateOf(true)
            var height = -1
            val scene = ImageComposeScene(600, 180, coroutineContext = clock.context) {
                AuroraTestTheme {
                    Box(Modifier.fillMaxWidth().onSizeChanged { height = it.height }) {
                        AuroraReplyAnswer(reply, touch, {}, stream.takeIf { streaming },
                            rememberSelectionState(), false, {})
                    }
                }
            }
            try {
                clock.frame(scene)
                assertEquals(0, height, "Thinking must not reserve padding for an empty future answer")
                stream.value = LiveText("previous-reply", "Старый ответ")
                clock.frame(scene)
                assertEquals(0, height)
                stream.value = LiveText(reply.id, "Первый фрагмент")
                clock.frame(scene)
                val node = assertNotNull(scene.semanticsOwners.firstNotNullOfOrNull { owner ->
                    owner.getAllSemanticsNodes(mergingEnabled = false).firstOrNull {
                        it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text ->
                            text.text == "Первый фрагмент"
                        }
                    }
                })
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(assertNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)(layouts))
                assertEquals(layouts.single().size.height + 24, height)
                val answerHeight = height
                reply = reply.copy(text = stream.value.text, status = ReplyStatus.Completed)
                streaming = false
                clock.frame(scene)
                clock.advance(scene, 160)
                assertEquals(answerHeight, height)
            } finally { scene.close() }
        }
    }
}
