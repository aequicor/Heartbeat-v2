package io.github.aequicor.heartbeat.designsystem.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import io.github.aequicor.heartbeat.designsystem.components.HbCheckbox
import io.github.aequicor.heartbeat.designsystem.components.HbRadioGroup
import io.github.aequicor.heartbeat.designsystem.components.HbTabs
import io.github.aequicor.heartbeat.designsystem.patterns.HbComposer
import io.github.aequicor.heartbeat.designsystem.patterns.HbComposerState
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interaction checks of the kit (HB-DS-040): the documented keyboard and pointer behavior is
 * exercised through semantics, so a change that breaks the contract fails the build instead of
 * waiting for a manual review.
 */
@OptIn(ExperimentalTestApi::class)
class KitInteractionTest {

    @Test
    fun tabsMoveSelectionWithArrowKeys() = runComposeUiTest {
        var selected by mutableStateOf("История")
        setContent {
            HbTheme {
                HbTabs(
                    tabs = listOf("История", "Настройки", "Модели"),
                    selected = selected,
                    onSelect = { selected = it },
                    tabLabel = { it },
                )
            }
        }

        onNodeWithText("История").requestFocus()
        onNodeWithText("История").performKeyInput { pressKey(Key.DirectionRight) }

        onNodeWithText("Настройки").assertIsSelected()
    }

    @Test
    fun radioGroupMovesSelectionWithArrowKeys() = runComposeUiTest {
        var selected by mutableStateOf("Точно")
        setContent {
            HbTheme {
                HbRadioGroup(
                    options = listOf("Точно", "Быстро"),
                    selected = selected,
                    onSelect = { selected = it },
                    optionLabel = { it },
                )
            }
        }

        onNodeWithText("Точно").requestFocus()
        onNodeWithText("Точно").performKeyInput { pressKey(Key.DirectionDown) }

        onNodeWithText("Быстро").assertIsSelected()
    }

    @Test
    fun checkboxTogglesOnClick() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent {
            HbTheme {
                HbCheckbox(
                    label = "Согласен",
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
            }
        }

        onNodeWithText("Согласен").performClick()

        assertTrue(checked)
    }

    @Test
    fun composerSendsOnEnter() = runComposeUiTest {
        var sent = 0
        var value by mutableStateOf("Привет")
        setContent {
            HbTheme {
                HbComposer(
                    value = value,
                    onValueChange = { value = it },
                    state = HbComposerState.Editing,
                    placeholder = "Сообщение",
                    label = "Черновик сообщения",
                    sendLabel = "Отправить",
                    stopLabel = "Остановить",
                    onSend = { sent++ },
                )
            }
        }

        onNodeWithText("Привет").performClick()
        onNodeWithText("Привет").performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, sent)
    }

    @Test
    fun composerKeepsShiftEnterAsNewline() = runComposeUiTest {
        var sent = 0
        var value by mutableStateOf("Привет")
        setContent {
            HbTheme {
                HbComposer(
                    value = value,
                    onValueChange = { value = it },
                    state = HbComposerState.Editing,
                    placeholder = "Сообщение",
                    label = "Черновик сообщения",
                    sendLabel = "Отправить",
                    stopLabel = "Остановить",
                    onSend = { sent++ },
                )
            }
        }

        onNodeWithText("Привет").performClick()
        onNodeWithText("Привет").performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Enter)
            keyUp(Key.ShiftLeft)
        }

        assertEquals(0, sent)
    }

    @Test
    fun composerDoesNotSendWhileGenerating() = runComposeUiTest {
        var sent = 0
        var value by mutableStateOf("Привет")
        setContent {
            HbTheme {
                HbComposer(
                    value = value,
                    onValueChange = { value = it },
                    state = HbComposerState.Generating,
                    placeholder = "Сообщение",
                    label = "Черновик сообщения",
                    sendLabel = "Отправить",
                    stopLabel = "Остановить",
                    onSend = { sent++ },
                )
            }
        }

        onNodeWithText("Привет").performClick()
        onNodeWithText("Привет").performKeyInput { pressKey(Key.Enter) }

        assertEquals(0, sent)
    }
}
