package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/** Focus visibility for controls rendered in a shared Desktop window, including custom controls. */
object HbFocusIndication {
    val keyboardNavigation: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyboardNavigation.current?.value ?: true
}

/** Records the input used to navigate the window without taking or clearing keyboard focus. */
@Composable
fun HbDesktopFocusIndication(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val keyboard = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalKeyboardNavigation provides keyboard) {
        Box(
            modifier
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key.isNavigationKey()) {
                        keyboard.value = true
                    }
                    false
                }
                .pointerInput(keyboard) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) keyboard.value = false
                        }
                    }
                },
        ) {
            content()
        }
    }
}

private fun Key.isNavigationKey() = when (this) {
    Key.Tab, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
    else -> false
}

// The mode belongs to the Desktop window and is read by focusable controls in its subtree.
@Suppress("CompositionLocalAllowlist")
private val LocalKeyboardNavigation = staticCompositionLocalOf<State<Boolean>?> { null }
