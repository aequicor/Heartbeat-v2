package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class AuroraChatHostTest {
    @Test
    fun openingAnotherChatRestoresItsAnchorAndClearsOutgoingFocus() {
        val first = chat("first", 3, 12)
        val next = chat("next", 13, 24)
        val directAnchor = Fixture(next).use {
            it.settle()
            it.anchor("next")
        }
        Fixture(first).use {
            it.settle()
            val editor = assertNotNull(it.editor())
            val point = editor.boundsInRoot.center
            it.scene.sendPointerEvent(PointerEventType.Move, point)
            it.scene.sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
            it.scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
            it.settle()
            assertEquals(true, it.editor()?.config?.getOrNull(SemanticsProperties.Focused))
            val firstReadEvents = it.readEvents("first")

            it.state = next
            it.draft.value = Draft("next", "Private to next")
            it.settle()
            assertEquals(directAnchor, it.anchor("next"))
            assertEquals(firstReadEvents, it.readEvents("first"))
            assertNull(it.text("first paragraph"))
            val newEditor = assertNotNull(it.editor())
            assertEquals("Private to next", newEditor.config.getOrNull(SemanticsProperties.EditableText)?.text)
            assertEquals(false, newEditor.config.getOrNull(SemanticsProperties.Focused))
        }
    }

    private class Fixture(initial: AiStudioScreenState) : AutoCloseable {
        val clock = AuroraHoverTestClock()
        val draft = MutableStateFlow(Draft(initial.chat?.id, "Private to ${initial.chat?.id}"))
        val live = MutableStateFlow(LiveText())
        val actions = mutableListOf<AiStudioAction.ReadAt>()
        var state by mutableStateOf(initial)
        val scene = ImageComposeScene(960, 640, coroutineContext = clock.context) {
            AuroraTestTheme {
                AuroraWorkspace(
                    state, draft, live, null, false,
                    onAction = { if (it is AiStudioAction.ReadAt) actions += it },
                    showNavigation = false, modifier = Modifier.fillMaxSize(),
                )
            }
        }

        fun settle() = repeat(8) { clock.advance(scene, 16) }
        fun node(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
            scene.semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(matches) }
        fun text(label: String) = node { entry ->
            entry.config.getOrNull(SemanticsProperties.Text).orEmpty().any { label in it.text }
        }
        fun editor() = node { it.config.getOrNull(SemanticsActions.SetText) != null }
        fun anchor(chat: String) = actions.last { it.chatId == chat }.anchor
        fun readEvents(chat: String) = actions.filter { it.chatId == chat }
        override fun close() = scene.close()
    }

    private companion object {
        fun chat(id: String, index: Int, offset: Int): AiStudioScreenState =
            AiStudioScreenState(
                chat = Chat(id, "$id chat", "workspace"),
                messages = List(30) { User("$id-$it", "", "$id paragraph $it") },
                restoreIndex = index,
                restoreAnchor = Anchor("$id-$index", offset),
            )

        fun SemanticsNode.find(matches: (SemanticsNode) -> Boolean): SemanticsNode? = when {
            !layoutInfo.isPlaced -> null
            matches(this) -> this
            else -> children.firstNotNullOfOrNull { it.find(matches) }
        }
    }
}
