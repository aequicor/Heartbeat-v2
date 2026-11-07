@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbChip
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Chat composer of the studio (HB-DS-020, HB-DS-021).
 *
 * The composer is a controlled editor: the draft, the state, the queue decision and the correction
 * capability belong to the feature store, and the component only renders them. Enter submits when
 * the owner allows it, Shift+Enter inserts a newline, and Ctrl/Cmd+Enter requests a correction when
 * the owner reports that correction is available. An IME-aware host disables [enterSubmits] while
 * a composition is active, because a confirmed composition must never send the message. The editor
 * grows to its preferred height and then scrolls, so the primary action stays reachable.
 */
@Composable
fun HbComposer(
    value: String,
    onValueChange: (String) -> Unit,
    state: HbComposerState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String = "",
    sendLabel: String = "",
    stopLabel: String = "",
    notice: String? = null,
    modelLabel: String? = null,
    enterSubmits: Boolean = true,
    correctionAvailable: Boolean = false,
    onSend: () -> Unit = {},
    onStop: () -> Unit = {},
    onCorrect: () -> Unit = {},
    attachments: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HbShapes.Medium)
            .background(colors.surface.input)
            .border(COMPOSER_BORDER, colors.border.control, HbShapes.Medium)
            .padding(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        attachments()
        HbTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = EDITOR_MAX_HEIGHT)
                .onPreviewKeyEvent { event ->
                    composerKeyEvent(
                        event = event,
                        state = state,
                        enterSubmits = enterSubmits,
                        correctionAvailable = correctionAvailable,
                        onSend = onSend,
                        onCorrect = onCorrect,
                    )
                },
            placeholder = placeholder,
            label = label,
            singleLine = false,
            minLines = 1,
            maxLines = Int.MAX_VALUE,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            if (modelLabel != null) {
                HbChip(label = modelLabel)
            }
            actions()
            Spacer(modifier = Modifier.weight(1f))
            ComposerAction(
                state = state,
                sendLabel = sendLabel,
                stopLabel = stopLabel,
                onSend = onSend,
                onStop = onStop,
            )
        }
        if (notice != null) {
            BasicText(
                text = notice,
                style = HbTypography.Caption,
                color = { noticeColor(state, colors) },
            )
        }
    }
}

@Composable
private fun ComposerAction(
    state: HbComposerState,
    sendLabel: String,
    stopLabel: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HbComposerState.Generating -> HbButton(
            text = stopLabel,
            onClick = onStop,
            modifier = modifier,
        )

        HbComposerState.Stopping -> HbButton(
            text = stopLabel,
            onClick = {},
            modifier = modifier,
            enabled = false,
        )

        HbComposerState.Editing -> HbButton(
            text = sendLabel,
            onClick = onSend,
            modifier = modifier,
        )

        HbComposerState.Failed -> HbButton(
            text = sendLabel,
            onClick = onSend,
            modifier = modifier,
        )

        HbComposerState.Empty,
        HbComposerState.PreparingAttachments,
        HbComposerState.Submitting,
        HbComposerState.Queued,
        HbComposerState.WaitingForApproval,
        HbComposerState.Unavailable,
        -> HbButton(
            text = sendLabel,
            onClick = onSend,
            modifier = modifier,
            enabled = false,
        )
    }
}

private fun composerKeyEvent(
    event: KeyEvent,
    state: HbComposerState,
    enterSubmits: Boolean,
    correctionAvailable: Boolean,
    onSend: () -> Unit,
    onCorrect: () -> Unit,
): Boolean = when {
    event.type != KeyEventType.KeyDown || event.key != Key.Enter || event.isShiftPressed -> false
    state == HbComposerState.Generating || state == HbComposerState.Stopping -> {
        if (event.isCtrlPressed || event.isMetaPressed) {
            if (correctionAvailable) onCorrect()
        }
        true
    }

    state == HbComposerState.Editing && enterSubmits -> {
        onSend()
        true
    }

    else -> false
}

private fun noticeColor(
    state: HbComposerState,
    colors: HbColors,
) = when (state) {
    HbComposerState.Failed, HbComposerState.Unavailable -> colors.status.error.foreground
    else -> colors.text.secondary
}

private val EDITOR_MAX_HEIGHT = 168.dp

private val COMPOSER_BORDER = 1.dp
