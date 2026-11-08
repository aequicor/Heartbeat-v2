package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbCheckbox
import io.github.aequicor.heartbeat.designsystem.components.HbDisclosure
import io.github.aequicor.heartbeat.designsystem.components.HbRadioGroup
import io.github.aequicor.heartbeat.designsystem.components.HbSegmentedControl
import io.github.aequicor.heartbeat.designsystem.components.HbTabs
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun busyButtonIgnoresClicks() = runComposeUiTest {
        var clicks = 0
        setContent {
            HbTheme {
                HbButton(
                    text = "Подключение…",
                    onClick = { clicks++ },
                    busy = true,
                )
            }
        }

        onNodeWithText("Подключение…").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun idleButtonCallsTheAction() = runComposeUiTest {
        var clicks = 0
        setContent {
            HbTheme {
                HbButton(text = "Сохранить", onClick = { clicks++ })
            }
        }

        onNodeWithText("Сохранить").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun segmentedControlMovesSelectionWithArrowKeys() = runComposeUiTest {
        var selected by mutableStateOf("Диалог")
        setContent {
            HbTheme {
                HbSegmentedControl(
                    options = listOf("Диалог", "Проект", "Терминал"),
                    selected = selected,
                    onSelect = { selected = it },
                    optionLabel = { it },
                )
            }
        }

        onNodeWithText("Диалог").requestFocus()
        onNodeWithText("Диалог").performKeyInput { pressKey(Key.DirectionRight) }

        onNodeWithText("Проект").assertIsSelected()
    }

    @Test
    fun disabledDisclosureDoesNotExpand() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        setContent {
            HbTheme {
                HbDisclosure(
                    title = "Раздел",
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    enabled = false,
                ) {
                    androidx.compose.foundation.text.BasicText("Скрытое содержимое")
                }
            }
        }

        onNodeWithText("Скрытое содержимое").assertDoesNotExist()
        onNodeWithText("Раздел").performClick()

        assertFalse(expanded)
        onNodeWithText("Скрытое содержимое").assertDoesNotExist()
    }
}
