@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aiengine.api.session.HistorySyncStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindow
import io.github.aequicor.heartbeat.features.aiengine.api.session.MessageWindowCursor
import io.github.aequicor.heartbeat.features.aiengine.api.session.PendingPermission
import io.github.aequicor.heartbeat.features.aiengine.api.session.PermissionOption
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallStatus
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Renders the studio workspace offscreen to a PNG for review. The test is a layout smoke check: it
 * runs without a desktop window and fails when the scene cannot produce a non-empty image.
 */
class AiStudioScreenScreenshotTest {

    @Test
    fun rendersWorkspace() {
        renderScreenshot(
            name = "ai-studio-workspace",
            width = WIDE_WIDTH,
            height = WIDE_HEIGHT,
            state = permissionState(),
        )
    }

    @Test
    fun rendersCompactWorkspace() {
        renderScreenshot(
            name = "ai-studio-workspace-compact",
            width = COMPACT_WIDTH,
            height = WIDE_HEIGHT,
            state = AiStudioState(engineAvailable = true),
        )
    }

    @Test
    fun rendersTouchWorkspace() {
        renderScreenshot(
            name = "ai-studio-workspace-touch",
            width = TOUCH_WIDTH,
            height = TOUCH_HEIGHT,
            density = HbDensity.Touch,
        )
    }

    private fun renderScreenshot(
        name: String,
        width: Int,
        height: Int,
        density: HbDensity = HbDensity.Dense,
        state: AiStudioState = AiStudioState(),
    ) {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f),
        ) {
            HbTheme(
                appearance = HbAppearance.Light,
                density = density,
            ) {
                AiStudioScreen(
                    state = state,
                    onAction = {},
                    avatarVisible = true,
                    onOpenSettings = {},
                )
            }
        }

        try {
            val data = assertNotNull(
                scene.render().encodeToData(EncodedImageFormat.PNG),
                "PNG encoding failed for $name",
            )
            val directory = File("build/screenshots").apply { mkdirs() }
            val file = File(directory, "$name.png")
            file.writeBytes(data.bytes)
            assertTrue(file.length() > 0, "Empty screenshot ${file.path}")
        } finally {
            scene.close()
        }
    }

    private fun permissionState(): AiStudioState {
        val tool = SessionTimelineItem.ToolCall("tool-read", "Чтение файлов проекта", ToolCallStatus.PENDING)
        val permission = PendingPermission(
            "request-read",
            tool.id,
            "Прочитать README.md",
            listOf(PermissionOption("allow", "Разрешить один раз", "allow_once")),
        )
        val transcript = listOf(
            SessionTimelineItem.UserMessage("user", listOf(SessionContent.Text("Проверь документацию проекта"))),
            SessionTimelineItem.AgentMessage("agent", listOf(SessionContent.Text("Начну с README.md"))),
            tool,
        )
        val engine = object : AiEngineKey {
            override val id = "dsh"
            override val name = "DeepSeek Harness"
            override val description = "Тестовый движок"
            override val connection = AiEngineConnection.ACP
        }
        val chat = SessionKey(engine, "documentation", title = "Документация проекта")
        return AiStudioState(
            engineAvailable = true,
            connection = AiEngineState.Ready,
            directory = "C:\\Projects\\Heartbeat",
            sessions = listOf(chat),
            selected = chat,
            session = SessionState(
                SessionStatus.AwaitingPermission(listOf(permission)),
                MessageWindow(transcript, MessageWindowCursor(), HistorySyncStatus.LOCAL_ONLY),
            ),
        )
    }

    private companion object {
        private const val WIDE_WIDTH = 1586

        private const val WIDE_HEIGHT = 992

        private const val COMPACT_WIDTH = 700

        private const val TOUCH_WIDTH = 390

        private const val TOUCH_HEIGHT = 844
    }
}
