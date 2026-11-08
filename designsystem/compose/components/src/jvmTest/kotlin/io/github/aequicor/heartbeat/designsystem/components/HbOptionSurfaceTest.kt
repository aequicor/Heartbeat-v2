package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bounds of the choice surfaces (HB-DS-019): an option list inside a small container takes the
 * container size instead of demanding its preferred minimum, so a popup in a narrow or short window
 * stays reachable instead of being clipped by the edge.
 */
@OptIn(ExperimentalTestApi::class)
class HbOptionSurfaceTest {

    @Test
    fun optionListFitsASmallContainer() = runComposeUiTest {
        setContent {
            HbTheme {
                Box(modifier = Modifier.size(width = 140.dp, height = 90.dp)) {
                    HbOptionList(
                        options = (1..20).map { "Вариант $it" },
                        selected = null,
                        activeIndex = 0,
                        optionLabel = { it },
                        onSelect = {},
                        onActiveChange = {},
                        onDismiss = {},
                        modifier = Modifier.testTag(ListTag),
                    )
                }
            }
        }

        val bounds = onNodeWithTag(ListTag).getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top

        assertTrue(width <= 140.dp, "The option list is $width wide in a 140-unit container")
        assertTrue(height <= 90.dp, "The option list is $height high in a 90-unit container")
        onNodeWithText("Вариант 2").assertExists()
    }

    private companion object {
        private const val ListTag = "option-list"
    }
}
