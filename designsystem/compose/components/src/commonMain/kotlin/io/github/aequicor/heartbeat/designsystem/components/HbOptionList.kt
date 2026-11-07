@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults

/**
 * Scrollable list of options shared by the choice surfaces (HB-DS-019).
 *
 * The list owns keyboard navigation inside the popup: arrows move the active option, Enter selects
 * it and Escape dismisses. It is a container of `HbMenuItem` rows, so an option is an action with a
 * chosen state, not a second kind of control.
 */
@Composable
internal fun <T> HbOptionList(
    options: List<T>,
    selected: T?,
    activeIndex: Int,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onActiveChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier
            .widthIn(min = POPUP_MIN_WIDTH, max = POPUP_MAX_WIDTH)
            .heightIn(max = POPUP_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .clip(HbShapes.Medium)
            .background(colors.surface.raised)
            .border(POPUP_BORDER, colors.border.subtle, HbShapes.Medium)
            .padding(HbSpacing.Xxs)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        onActiveChange((activeIndex + 1).coerceAtMost(options.lastIndex))
                        true
                    }

                    Key.DirectionUp -> {
                        onActiveChange((activeIndex - 1).coerceAtLeast(0))
                        true
                    }

                    Key.Enter -> {
                        options.getOrNull(activeIndex)?.let(onSelect)
                        true
                    }

                    Key.Escape -> {
                        onDismiss()
                        true
                    }

                    else -> false
                }
            },
    ) {
        options.forEachIndexed { index, option ->
            HbMenuItem(
                label = optionLabel(option),
                onClick = { onSelect(option) },
                selected = option == selected,
                modifier = if (index == activeIndex) {
                    Modifier.background(colors.surface.subtle)
                } else {
                    Modifier
                },
            )
        }
    }
}

private val POPUP_MIN_WIDTH = 200.dp

private val POPUP_MAX_WIDTH = 420.dp

private val POPUP_MAX_HEIGHT = 280.dp

private val POPUP_BORDER = 1.dp
