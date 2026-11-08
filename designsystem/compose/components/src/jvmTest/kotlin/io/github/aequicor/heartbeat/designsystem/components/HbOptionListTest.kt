package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lazy-list keys of the option list (HB-DS-019). A nullable `optionKey` must not fall back to the
 * item index: the index can equal a real key and moves when the list is reordered.
 */
@OptIn(ExperimentalTestApi::class)
class HbOptionListTest {

    @Test
    fun nullableOptionKeyDoesNotCollideWithAnIndexKey() = runComposeUiTest {
        setContent {
            HbTheme {
                Box(Modifier.size(width = 320.dp, height = 240.dp)) {
                    HbOptionList(
                        options = listOf(
                            KeyedOption("Первый", 1),
                            KeyedOption("Второй", null),
                        ),
                        selected = null,
                        activeIndex = 0,
                        optionLabel = { it.label },
                        onSelect = {},
                        onActiveChange = {},
                        onDismiss = {},
                        optionKey = { it.key },
                    )
                }
            }
        }

        onNodeWithText("Второй").assertExists()
    }

    @Test
    fun nullKeyKeepsItsIdentityWhenOptionsAreReordered() {
        val options = listOf(
            KeyedOption("Первый", null),
            KeyedOption("Второй", 1),
            KeyedOption("Третий", 2),
        )

        val keys = optionListKeys(options, optionKey = { it.key })
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys[0] is String, "The null identity must use a Bundle-saveable string key")

        val reorderedKeys = optionListKeys(options.reversed(), optionKey = { it.key })
        assertEquals(keys[0], reorderedKeys[2])
    }

    @Test
    fun nullKeyDoesNotReuseARealKeyWithTheSameText() {
        val options = listOf(
            KeyedOption("Первый", "sentinel"),
            KeyedOption("Второй", null),
        )

        val keys = optionListKeys(options, { it.key }, nullKey = "sentinel")
        assertEquals(2, keys.toSet().size)
    }

    @Test
    fun everyNullIdentityGetsItsOwnListKey() {
        val options = listOf(
            KeyedOption("Первый", null),
            KeyedOption("Второй", null),
        )

        val keys = optionListKeys(options, optionKey = { it.key })
        assertEquals(2, keys.toSet().size)
    }

    private data class KeyedOption(val label: String, val key: Any?)
}
