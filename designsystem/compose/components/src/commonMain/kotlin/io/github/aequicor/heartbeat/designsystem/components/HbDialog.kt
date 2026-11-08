package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Bounded blocking decision (HB-DS-028).
 *
 * The dialog is a platform dialog window, so the host keeps the modality and the focus containment
 * of its platform; the content also draws the application scrim of the contract, so the decision is
 * visibly modal on every renderer. A click outside the panel requests dismissal when
 * [dismissOnClickOutside] is set, the title is exposed as a heading, and a platform dismiss request
 * — Escape or a close gesture — reaches [onDismissRequest] only when [dismissOnBackPress] is set by
 * the platform properties. Each flag controls only its own dismissal path: a disabled back press
 * does not swallow an enabled click outside. A blocking decision passes both flags as `false`, so
 * the surface cannot be dismissed by an accidental backdrop click while the choice is still
 * required. The panel is capped at the dialog width of the contract and keeps its header and
 * [actions] outside the scrollable content.
 */
@Composable
fun HbDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbTheme.colors
    val darkCanvas = colors.surface.canvas.luminance() < DarkCanvasLuminance
    val scrim = if (darkCanvas) HbEffects.ScrimDark else HbEffects.ScrimLight

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .pointerInput(dismissOnClickOutside) {
                    detectTapGestures {
                        if (dismissOnClickOutside) onDismissRequest()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = DIALOG_MAX_WIDTH)
                    .heightIn(max = DIALOG_MAX_HEIGHT)
                    .clip(HbShapes.Large)
                    .background(colors.surface.raised)
                    .border(DIALOG_BORDER, colors.border.subtle, HbShapes.Large)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(HbSpacing.M),
                verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
            ) {
                BasicText(
                    text = title,
                    style = HbTypography.Title,
                    color = { colors.text.primary },
                    modifier = Modifier.semantics { heading() },
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
                    content = content,
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
                    content = actions,
                )
            }
        }
    }
}

private val DIALOG_MAX_WIDTH = 560.dp

private val DIALOG_MAX_HEIGHT = 560.dp

private val DIALOG_BORDER = 1.dp

private const val DarkCanvasLuminance = 0.5f
