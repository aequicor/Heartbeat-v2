@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Answer
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Question
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Request
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraRequestPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuroraRequestPanelTest {
    private val clock = AuroraHoverTestClock()
    private val question = Question("format", "Как сохранить ответ?", listOf("Текст", "Отчёт"), freeText = true)
    private val request = Request.Questions("questions", listOf(question), timed = true)

    @Test
    fun hoverAndScrollStopTheTimerWithoutEditingAnAnswer() {
        val actions = mutableListOf<AiStudioAction>()
        withPanel(request, actions) {
            sendPointerEvent(PointerEventType.Move, Offset(180f, 38f))
            clock.frame(this)
            assertEquals(listOf<AiStudioAction>(AiStudioAction.InteractWithRequest(request.id)), actions)
            sendPointerEvent(PointerEventType.Scroll, Offset(180f, 70f), scrollDelta = Offset(0f, 1f))
            clock.frame(this)
            assertEquals(1, actions.size)
        }
    }

    @Test
    fun keyboardFocusStopsTheTimerAndArrowSelectsTheNextRadioOption() {
        val actions = mutableListOf<AiStudioAction>()
        withPanel(request, actions) {
            tap(Key.Tab)
            assertEquals(listOf<AiStudioAction>(AiStudioAction.InteractWithRequest(request.id)), actions)
            click("Текст")
            tap(Key.DirectionDown)
            assertEquals(
                listOf(
                    AiStudioAction.InteractWithRequest(request.id),
                    AiStudioAction.Choose(request.id, question.id, "Текст"),
                    AiStudioAction.Choose(request.id, question.id, "Отчёт"),
                ),
                actions,
            )
        }
    }

    @Test
    fun narrowDesktopQuestionKeepsLongOptionsAndCommentAvailableAtLargeTextSize() {
        val option = "Сохранить текст, вложения и позицию чтения после остановки"
        val longQuestion = question.copy(options = listOf(option, "Только текст"), required = true)
        val panel = request.copy(
            questions = listOf(longQuestion), answers = mapOf(longQuestion.id to Answer(options = setOf(option))),
        )
        val actions = mutableListOf<AiStudioAction>()
        withPanel(panel, actions, width = 320, height = 360, textScale = 2f) {
            val optionBounds = assertNotNull(findNode { it.hasText(option) }).boundsInRoot
            assertTrue(optionBounds.height > 44f)
            val comment = assertNotNull(findNode {
                "Комментарий…" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            })
            val submit = assertNotNull(findNode { it.hasText("Отправить") }).boundsInRoot
            assertTrue(comment.boundsInRoot.top >= optionBounds.bottom)
            assertTrue(submit.top >= comment.boundsInRoot.bottom)
            assertTrue(submit.bottom <= 360f)

            val setText = assertNotNull(comment.config.getOrNull(SemanticsActions.SetText)?.action)
            assertTrue(setText(AnnotatedString("Сохранить черновик")))
            clock.frame(this)
            assertTrue(AiStudioAction.EditAnswer(panel.id, longQuestion.id, "Сохранить черновик") in actions)
            click("Отправить")
            assertEquals(AiStudioAction.SubmitAnswers(panel.id), actions.last())
        }
    }

    @Test
    fun strictPermissionRequiresAnExplicitDecision() {
        val actions = mutableListOf<AiStudioAction>()
        val permission = Request.Permission("permission", "Разрешить запуск?", "Проверка", "--check", "Один шаг")
        withPanel(permission, actions) {
            sendPointerEvent(PointerEventType.Move, Offset(180f, 38f))
            tap(Key.Enter)
            assertTrue(actions.isEmpty())
            click("Отклонить")
            assertEquals(listOf<AiStudioAction>(AiStudioAction.AnswerPermission(permission.id, false)), actions)
        }
    }

    private fun withPanel(
        request: Request,
        actions: MutableList<AiStudioAction>,
        width: Int = 648,
        height: Int = 300,
        textScale: Float = 1f,
        usePanel: ImageComposeScene.() -> Unit,
    ) {
        val scene = ImageComposeScene(width, height, coroutineContext = clock.context) {
            AuroraTestTheme(appearance = HbAppearance.Light, textScale = textScale) {
                HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                    AuroraRequestPanel(request, false, { actions.add(it) }, inlineStop = false)
                }
            }
        }
        try {
            clock.frame(scene)
            scene.usePanel()
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.tap(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        clock.frame(this)
    }

    private fun ImageComposeScene.click(label: String) {
        val node = assertNotNull(findNode { it.hasText(label) })
        val point = node.boundsInRoot.center
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        clock.frame(this)
    }

    private fun SemanticsNode.hasText(label: String) = config.getOrNull(SemanticsProperties.Text).orEmpty()
        .any { it.text == label }

    private fun ImageComposeScene.findNode(matches: (SemanticsNode) -> Boolean) =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.findNode(matches) }

    private fun SemanticsNode.findNode(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (matches(this)) this else children.firstNotNullOfOrNull { it.findNode(matches) }
}
