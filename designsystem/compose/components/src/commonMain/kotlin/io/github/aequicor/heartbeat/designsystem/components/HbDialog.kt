package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Bounded blocking decision (HB-DS-028).
 *
 * The dialog is a platform dialog window, so the host keeps the modality, the scrim and the focus
 * containment of its platform; the title is exposed as a heading and [onDismissRequest] receives an
 * Escape or a close request. The panel is capped at the dialog width of the contract and keeps its
 * header and [actions] outside the scrollable content.
 */
@Composable
fun HbDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = modifier
                .widthIn(max = DIALOG_MAX_WIDTH)
                .clip(HbShapes.Large)
                .background(colors.surface.raised)
                .border(DIALOG_BORDER, colors.border.subtle, HbShapes.Large)
                .padding(HbSpacing.M),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            BasicText(
                text = title,
                style = HbTypography.Title,
                color = { colors.text.primary },
                modifier = Modifier.semantics { heading() },
            )
            content()
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
                content = actions,
            )
        }
    }
}

private val DIALOG_MAX_WIDTH = 560.dp

private val DIALOG_BORDER = 1.dp
