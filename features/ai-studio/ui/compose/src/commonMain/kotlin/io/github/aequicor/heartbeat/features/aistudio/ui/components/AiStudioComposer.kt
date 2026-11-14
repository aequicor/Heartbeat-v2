package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState

@Composable
internal fun AiStudioComposerBar(state: AiStudioScreenState, onAction: (AiStudioAction) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(HbSpacing.M), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = HbPaneGeometry.ReadingMax.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        ) {
            HbTextField(
                value = if (state.turnActive) "" else state.draft,
                onValueChange = { onAction(AiStudioAction.EditDraft(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = "Сообщение",
                placeholder = if (state.turnActive) "DeepSeek Harness выполняет запрос…" else "Опишите задачу",
                enabled = state.canEditDraft,
                singleLine = false,
                maxLines = ComposerMaxLines,
            )
            val failure = state.sessionStatus as? SessionStatus.Failed
            if (failure != null) {
                val colors = HbTheme.colors
                BasicText(failure.reason, style = HbTypography.Body, color = { colors.status.error.foreground })
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (state.turnActive) {
                    HbButton(
                        "Остановить",
                        onClick = { onAction(AiStudioAction.CancelTurn) },
                        variant = HbButtonVariant.Secondary,
                    )
                } else {
                    HbButton("Отправить", onClick = { onAction(AiStudioAction.Send) }, enabled = state.canSend)
                }
            }
        }
    }
}

private const val ComposerMaxLines = 4
