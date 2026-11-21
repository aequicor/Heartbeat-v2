package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test

/**
 * Tooltip checks (HB-DS-029): the hint appears after the hover delay and stays while the trigger
 * is hovered.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipInteractionTest {

    @Test
    fun tooltipAppearsAfterHoverDelay() = runComposeUiTest {
        setContent {
            HbTheme {
                HbTooltip(text = "Подсказка") {
                    BasicText("Триггер")
                }
            }
        }

        onNodeWithText("Триггер").performMouseInput { enter() }
        waitUntil(timeoutMillis = 3_000) {
            onAllNodes(hasText("Подсказка")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun mouseClickDoesNotKeepTheHintAfterPointerExit() = runComposeUiTest {
        setContent {
            HbTheme {
                HbDesktopFocusIndication {
                    HbTooltip("Подсказка") {
                        BasicText("Триггер", Modifier.clickable {})
                    }
                }
            }
        }
        onNodeWithText("Триггер").performMouseInput { enter() }
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("Подсказка")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Триггер").performMouseInput { click(); exit() }
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("Подсказка")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun deactivationCancelsPendingAndVisibleHintsWithoutReopeningOnReturn() = runComposeUiTest {
        var windowFocused by mutableStateOf(true)
        setContent {
            val parent = LocalWindowInfo.current
            val window = remember(parent) {
                object : WindowInfo by parent {
                    override val isWindowFocused get() = windowFocused
                }
            }
            CompositionLocalProvider(LocalWindowInfo provides window) {
                HbTheme {
                    HbTooltip("Подсказка") { BasicText("Триггер") }
                }
            }
        }
        onNodeWithText("Триггер").performMouseInput { enter() }
        runOnIdle { windowFocused = false }
        onNodeWithText("Подсказка").assertDoesNotExist()
        runOnIdle { windowFocused = true }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Подсказка").assertDoesNotExist()
        onNodeWithText("Триггер").performMouseInput { exit(); enter() }
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("Подсказка")).fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { windowFocused = false }
        onNodeWithText("Подсказка").assertDoesNotExist()
        runOnIdle { windowFocused = true }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Подсказка").assertDoesNotExist()
    }

    @Test
    fun keyboardFocusKeepsTheHintUntilEscapeOrFocusMoves() = runComposeUiTest {
        setContent {
            HbTheme {
                HbDesktopFocusIndication {
                    Column {
                        HbTooltip("Подсказка") { BasicText("Триггер", Modifier.clickable {}) }
                        BasicText("Другой", Modifier.clickable {})
                    }
                }
            }
        }
        onNodeWithText("Другой").performMouseInput { enter(); click(); exit() }
        onNodeWithText("Другой").performKeyInput { pressKey(Key.Tab) }
        onNodeWithText("Триггер").assertIsFocused()
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("Подсказка")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Триггер").performKeyInput { pressKey(Key.Escape) }
        onNodeWithText("Подсказка").assertDoesNotExist()
        onNodeWithText("Триггер").performKeyInput { pressKey(Key.Tab) }
        onNodeWithText("Другой").assertIsFocused()
        onNodeWithText("Другой").performKeyInput { pressKey(Key.Tab) }
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("Подсказка")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Триггер").performKeyInput { pressKey(Key.Tab) }
        onNodeWithText("Другой").assertIsFocused()
        waitUntil(timeoutMillis = 3_000) { onAllNodes(hasText("Подсказка")).fetchSemanticsNodes().isEmpty() }
    }
}
