package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuSeparator
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Menu contract checks (HB-DS-019, HB-DS-040): the trigger keeps the focus, and a decorative
 * separator neither takes focus nor swallows an activation.
 */
@OptIn(ExperimentalTestApi::class)
class MenuInteractionTest {

    @Test
    fun menuReturnsFocusToTheTriggerWhenDismissedByKeyboard() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        setContent {
            HbTheme {
                HbMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    actions = listOf(HbMenuAction(label = "Открыть", onClick = {})),
                    trigger = { triggerModifier ->
                        HbButton(
                            text = "Действия",
                            onClick = { expanded = true },
                            modifier = triggerModifier,
                        )
                    },
                )
            }
        }

        onNodeWithText("Действия").performClick()
        onNodeWithText("Открыть").assertIsDisplayed()

        onNodeWithText("Открыть").performKeyInput { pressKey(Key.Escape) }

        onNodeWithText("Открыть").assertDoesNotExist()
        onNodeWithText("Действия").assertIsFocused()
    }

    @Test
    fun separatorIsNotFocusableAndDoesNotSwallowActivation() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var selected by mutableStateOf<String?>(null)
        setContent {
            HbTheme {
                HbMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    actions = listOf(
                        HbMenuAction(label = "Переименовать", onClick = { selected = "Переименовать" }),
                        HbMenuSeparator,
                        HbMenuAction(label = "Архивировать", onClick = { selected = "Архивировать" }),
                    ),
                    trigger = { triggerModifier ->
                        HbButton(
                            text = "Действия",
                            onClick = { expanded = true },
                            modifier = triggerModifier,
                        )
                    },
                )
            }
        }

        onNodeWithText("Действия").performClick()
        onNodeWithText("Переименовать").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }

        assertEquals("Архивировать", selected)
    }
}
