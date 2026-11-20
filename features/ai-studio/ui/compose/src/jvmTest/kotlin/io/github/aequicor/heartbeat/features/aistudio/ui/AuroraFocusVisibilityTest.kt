@file:Suppress("MagicNumber")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuroraFocusVisibilityTest {
    @Test
    fun tracksNavigationAndMouseWithoutClearingActualFocus() {
        val requesters = List(2) { FocusRequester() }
        var focused by mutableIntStateOf(-1)
        var visible = false
        val scene = ImageComposeScene(160, 48) {
            HbDesktopFocusIndication(Modifier.fillMaxSize()) {
                Row {
                    requesters.forEachIndexed { index, requester ->
                        Box(
                            Modifier.size(80.dp, 48.dp).focusRequester(requester)
                                .onFocusChanged { if (it.isFocused) focused = index }
                                .clickable { requester.requestFocus() },
                        )
                    }
                }
                visible = focused >= 0 && HbFocusIndication.keyboardNavigation
            }
        }
        try {
            scene.render().close()
            scene.click(Offset(40f, 24f))
            assertEquals(0, focused)
            assertFalse(visible)

            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyDown))
            scene.render().close()
            assertEquals(1, focused)
            assertTrue(visible)

            scene.click(Offset(120f, 24f))
            assertEquals(1, focused)
            assertFalse(visible)
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.click(point: Offset) {
        sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
        render().close()
    }
}
