package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.aequicor.heartbeat.designsystem.components.HbCombobox
import io.github.aequicor.heartbeat.designsystem.components.HbSelect
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Choice surface checks (HB-DS-018, HB-DS-040): the closed list and the searchable list open their
 * options and report the selected value to the owner.
 */
@OptIn(ExperimentalTestApi::class)
class ChoiceInteractionTest {

    @Test
    fun selectOpensOptionsAndSelectsOne() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        setContent {
            HbTheme {
                HbSelect(
                    options = listOf("heartbeat-fast", "heartbeat-deep"),
                    selected = selected,
                    onSelect = { selected = it },
                    placeholder = "Выберите модель",
                    optionLabel = { it },
                )
            }
        }

        onNodeWithText("Выберите модель").performClick()
        onNodeWithText("heartbeat-deep").performClick()

        assertEquals("heartbeat-deep", selected)
    }

    @Test
    fun comboboxShowsNoResultsForAnUnmatchedQuery() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            HbTheme {
                HbCombobox(
                    options = listOf<String>(),
                    query = query,
                    onQueryChange = { query = it },
                    onSelect = {},
                    placeholder = "Подключение",
                    label = "Подключение",
                    noResultsText = "Ничего не найдено",
                )
            }
        }

        onNodeWithContentDescription("Подключение").performClick()
        onNodeWithContentDescription("Подключение").performTextInput("xyz")

        onNodeWithText("Ничего не найдено").assertIsDisplayed()
    }

    @Test
    fun comboboxKeepsEditingAcrossOpenSuggestions() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            HbTheme {
                HbCombobox(
                    options = listOf("alpha", "beta"),
                    query = query,
                    onQueryChange = { query = it },
                    onSelect = {},
                    label = "Поиск",
                    optionLabel = { it },
                )
            }
        }

        onNodeWithContentDescription("Поиск").performClick()
        onNodeWithContentDescription("Поиск").performTextInput("al")
        onNodeWithContentDescription("Поиск").performKeyInput { pressKey(Key.Backspace) }

        assertEquals("a", query)
    }

    @Test
    fun comboboxSelectsActiveSuggestionOnEnter() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        setContent {
            HbTheme {
                HbCombobox(
                    options = listOf("Локальная среда", "Удалённый сервер"),
                    query = "",
                    onQueryChange = {},
                    onSelect = { selected = it },
                    label = "Подключение",
                    optionLabel = { it },
                )
            }
        }

        onNodeWithContentDescription("Подключение").performClick()
        onNodeWithContentDescription("Подключение").performKeyInput { pressKey(Key.Enter) }

        assertEquals("Локальная среда", selected)
    }

    @Test
    fun comboboxDismissesSuggestionsOnEscape() = runComposeUiTest {
        setContent {
            HbTheme {
                HbCombobox(
                    options = listOf("Локальная среда", "Удалённый сервер"),
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    label = "Подключение",
                    optionLabel = { it },
                )
            }
        }

        onNodeWithContentDescription("Подключение").performClick()
        onNodeWithText("Локальная среда").assertIsDisplayed()
        onNodeWithContentDescription("Подключение").performKeyInput { pressKey(Key.Escape) }

        onNodeWithText("Локальная среда").assertDoesNotExist()
    }

    @Test
    fun comboboxClampsTheActiveIndexToTheLastOption() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        setContent {
            HbTheme {
                HbCombobox(
                    options = listOf("Первая", "Вторая"),
                    query = "",
                    onQueryChange = {},
                    onSelect = { selected = it },
                    label = "Поле",
                    optionLabel = { it },
                )
            }
        }

        onNodeWithContentDescription("Поле").performClick()
        onNodeWithContentDescription("Поле").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }

        assertEquals("Вторая", selected)
    }

    @Test
    fun comboboxClosesThePopupWhenDisabled() = runComposeUiTest {
        var enabled by mutableStateOf(true)
        setContent {
            HbTheme {
                HbCombobox(
                    options = listOf("Первая", "Вторая"),
                    query = "",
                    onQueryChange = {},
                    onSelect = {},
                    label = "Поле",
                    enabled = enabled,
                    optionLabel = { it },
                )
            }
        }

        onNodeWithContentDescription("Поле").performClick()
        onNodeWithText("Первая").assertIsDisplayed()

        enabled = false
        waitForIdle()

        onNodeWithText("Первая").assertDoesNotExist()
    }
}
