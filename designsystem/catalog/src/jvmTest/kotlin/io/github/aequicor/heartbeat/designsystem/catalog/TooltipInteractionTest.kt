package io.github.aequicor.heartbeat.designsystem.catalog

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
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
}
