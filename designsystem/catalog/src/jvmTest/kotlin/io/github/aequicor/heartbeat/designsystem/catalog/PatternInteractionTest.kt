package io.github.aequicor.heartbeat.designsystem.catalog

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.patterns.HbApprovalRequest
import io.github.aequicor.heartbeat.designsystem.patterns.HbApprovalState
import io.github.aequicor.heartbeat.designsystem.patterns.HbMessage
import io.github.aequicor.heartbeat.designsystem.patterns.HbMessageRole
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test

/**
 * Pattern state checks (HB-DS-030, HB-DS-023, HB-DS-040): a resolved approval loses its decisions,
 * and a message exposes its role to assistive services.
 */
@OptIn(ExperimentalTestApi::class)
class PatternInteractionTest {

    @Test
    fun pendingApprovalShowsDecisions() = runComposeUiTest {
        setContent {
            HbTheme {
                HbApprovalRequest(
                    action = "Записать файл",
                    target = "HbButton.kt",
                    decisions = {
                        HbButton(text = "Разрешить один раз", onClick = {})
                    },
                )
            }
        }

        onNodeWithText("Разрешить один раз").assertIsDisplayed()
    }

    @Test
    fun resolvedApprovalHidesDecisions() = runComposeUiTest {
        setContent {
            HbTheme {
                HbApprovalRequest(
                    action = "Записать файл",
                    target = "HbButton.kt",
                    state = HbApprovalState.Allowed,
                    decisions = {
                        HbButton(text = "Разрешить один раз", onClick = {})
                    },
                )
            }
        }

        onNodeWithText("Разрешить один раз").assertDoesNotExist()
    }

    @Test
    fun messageExposesItsRole() = runComposeUiTest {
        setContent {
            HbTheme {
                HbMessage(role = HbMessageRole.Assistant, roleLabel = "Ассистент") {
                    BasicText("Ответ модели")
                }
            }
        }

        onNodeWithContentDescription("Ассистент").assertExists()
    }
}
