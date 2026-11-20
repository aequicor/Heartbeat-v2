@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.features.aistudio.di.DefaultAiStudioStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Anchor
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.ui.components.loadAuroraBackdrop
import jdk.jfr.Configuration
import jdk.jfr.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuroraProfileTest {
    @Test
    @Suppress("InjectDispatcher")
    fun profilesDesktopInputAndScrollingOverTheMockStream() = runBlocking {
        Assume.assumeTrue(System.getenv("HEARTBEAT_PROFILE_AURORA") == "1")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = DefaultAiStudioStoreFactory(AuroraTestPreferences).create(scope)

        try {
            withTimeout(5_000) { store.screenState.first { !it.loading } }
            val longChat = store.screenState.value.chats.single { it.title == "Длинная история" }
            store.dispatch(AiStudioAction.OpenChat(longChat.id))
            val history = withTimeout(5_000) { store.screenState.first { it.chat?.id == longChat.id } }
            assertEquals(2_500, history.messages.size)
            profile(store)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun profile(store: AiStudioStore) {
        val backdrop = loadAuroraBackdrop()
        val anchor = AtomicReference<Anchor>()
        val clock = TestCoroutineScheduler()
        val scene = ImageComposeScene(
            1_440, 900, density = Density(1f), coroutineContext = StandardTestDispatcher(clock),
        ) {
            val state by store.screenState.collectAsState()
            AuroraTestTheme(appearance = HbAppearance.Light) {
                AiStudioScreen(
                    state, store.draft, store.liveText, backdrop,
                    onAction = { action ->
                        if (action is AiStudioAction.ReadAt && action.anchor.messageId != null &&
                            store.liveText.value.replyId != null
                        ) {
                            anchor.set(action.anchor)
                        }
                        store.dispatch(action)
                    },
                    onOpenSettings = {}, avatarVisible = false,
                )
            }
        }
        try {
            repeat(4) { scene.renderFrame(clock) }
            scene.edit("Проверка Desktop")
            store.dispatch(AiStudioAction.Send)
            withTimeout(5_000) { store.liveText.first { it.text.isNotEmpty() } }
            scene.renderFrame(clock)
            record(scene, clock) { store.liveText.value.replyId != null }
            withTimeout(5_000) { store.draft.first { it.text == "Черновик во время потока 60" } }
            assertNotNull(anchor.get()?.messageId, "Wheel input must move the history during the stream")
            val reply = withTimeout(15_000) {
                store.screenState.first { (it.messages.firstOrNull() as? Reply)?.status == ReplyStatus.Completed }
            }.messages.first() as Reply
            assertNotNull(reply.result)
        } finally {
            scene.close()
        }
    }

    private suspend fun record(scene: ImageComposeScene, clock: TestCoroutineScheduler, isStreaming: () -> Boolean) {
        val directory = Path.of("build", "profiles").createDirectories()
        val pointer = Offset(940f, 420f)
        scene.sendPointerEvent(PointerEventType.Move, pointer)
        scene.renderFrame(clock)
        var activeSamples = 0
        val inputTimes = mutableListOf<Long>()
        Recording(Configuration.getConfiguration("profile")).use { recording ->
            recording.start()
            val times = buildList {
                repeat(61) { frame ->
                    if (isStreaming()) activeSamples++
                    add(measureNanoTime {
                        inputTimes += measureNanoTime {
                            if (frame % 5 == 0) scene.edit("Черновик во время потока $frame")
                            scene.sendPointerEvent(
                                PointerEventType.Scroll, pointer, scrollDelta = Offset(0f, -2f),
                            )
                        }
                        scene.renderFrame(clock)
                    })
                    delay(45)
                }
            }
            recording.stop()
            recording.dump(directory.resolve("aurora-desktop.jfr"))
            directory.resolve("aurora-desktop.txt").toFile()
                .writeText(
                    "Samples=${times.size}; active stream=$activeSamples\n" +
                        "p95 dispatch=${inputTimes.p95Millis()} ms\n" +
                        "p95 dispatch+render=${times.p95Millis()} ms\n",
                )
            assertTrue(activeSamples > times.size / 2, "The profile must exercise input under an active mock stream")
        }
    }

    private fun List<Long>.p95Millis() = sorted()[(lastIndex * 0.95).toInt()] / 1_000_000.0

    private fun ImageComposeScene.renderFrame(clock: TestCoroutineScheduler) {
        clock.advanceTimeBy(16)
        clock.runCurrent()
        render(clock.currentTime * 1_000_000L).close()
        clock.runCurrent()
    }

    private fun ImageComposeScene.edit(text: String) {
        val root = semanticsOwners.first().rootSemanticsNode
        val edit = assertNotNull(root.editAction())
        assertTrue(edit(AnnotatedString(text)))
    }

    private fun SemanticsNode.editAction(): ((AnnotatedString) -> Boolean)? {
        val label = config.getOrNull(SemanticsProperties.ContentDescription)
        if (label?.contains("Сообщение") == true) {
            val action = config.getOrNull(SemanticsActions.SetText)?.action
            if (action != null) return action
        }
        return children.firstNotNullOfOrNull { it.editAction() }
    }


}
