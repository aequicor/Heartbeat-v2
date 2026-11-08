package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.aequicor.heartbeat.designsystem.components.HbDisclosure
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Interaction checks of the rendered kit (HB-DS-040): the switch and the disclosure keep their
 * documented behavior, so a change that breaks a state contract fails the build.
 */
@OptIn(ExperimentalTestApi::class)
class StateInteractionTest {

    @Test
    fun switchTogglesOnClick() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent {
            HbTheme {
                HbSwitch(
                    label = "Автозапуск",
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
            }
        }

        onNodeWithText("Автозапуск").performClick()

        assertTrue(checked)
    }

    @Test
    fun disclosureRevealsContentAfterExpanding() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        setContent {
            HbTheme {
                HbDisclosure(
                    title = "Активность инструмента",
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто",
                ) {
                    androidx.compose.foundation.text.BasicText("read_file · 12 строк")
                }
            }
        }

        onNodeWithText("read_file · 12 строк").assertDoesNotExist()

        onNodeWithText("Активность инструмента").performClick()
        onNodeWithText("read_file · 12 строк").assertIsDisplayed()

        onNodeWithText("Активность инструмента").performClick()
        onNodeWithText("read_file · 12 строк").assertDoesNotExist()
    }
}
