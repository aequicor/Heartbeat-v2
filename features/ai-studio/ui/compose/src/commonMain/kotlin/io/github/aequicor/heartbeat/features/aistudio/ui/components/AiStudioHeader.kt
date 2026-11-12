package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineState
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioState

@Composable
internal fun AiStudioHeader(
    state: AiStudioState,
    onAction: (AiStudioAction) -> Unit,
    onShowChats: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = HbSpacing.M, vertical = HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            AiStudioBrandMark(Modifier.width(HbSpacing.Xl).height(HbSpacing.S))
            BasicText(
                "DeepSeek Harness",
                style = HbTypography.PaneTitle,
                color = { colors.text.primary },
            )
        }
        BasicText(
            text = state.connectionLabel(),
            style = HbTypography.Caption,
            color = { if (state.connected) colors.status.success.foreground else colors.text.secondary },
        )
        if (state.connected) {
            BasicText(
                state.directory,
                style = HbTypography.Code,
                color = { colors.text.secondary },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
                if (onShowChats != null) {
                    HbButton("Чаты", onShowChats, variant = HbButtonVariant.Secondary, enabled = !state.turnActive)
                }
                HbButton(
                    "Отключить",
                    onClick = { onAction(AiStudioAction.Disconnect) },
                    enabled = !state.busy && !state.turnActive,
                    variant = HbButtonVariant.Ghost,
                )
            }
        } else if (state.engineAvailable) {
            AiStudioConnectionControls(state, onAction)
        }
        val error = state.error ?: (state.connection as? AiEngineState.Failed)?.reason
        if (error != null) {
            BasicText(error, style = HbTypography.Body, color = { colors.status.error.foreground })
        }
    }
}

@Composable
private fun AiStudioConnectionControls(state: AiStudioState, onAction: (AiStudioAction) -> Unit) {
    HbTextField(
        value = state.directory,
        onValueChange = { onAction(AiStudioAction.EditDirectory(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = "Рабочий каталог",
        placeholder = "Абсолютный путь к проекту",
        enabled = !state.busy,
    )
    HbButton(
        text = if (state.connection is AiEngineState.Failed) "Повторить подключение" else "Подключить",
        onClick = { onAction(AiStudioAction.Connect) },
        enabled = state.directory.isNotBlank() && state.connection != AiEngineState.Connecting,
        busy = state.busy,
        busyLabel = "Подключение к DeepSeek Harness",
    )
}

private fun AiStudioState.connectionLabel(): String = if (!engineAvailable) {
    "Движок недоступен"
} else {
    when (connection) {
        AiEngineState.NotInitialized -> "Не подключён"
        AiEngineState.Connecting -> "Подключение…"
        AiEngineState.Ready -> "Подключён"
        is AiEngineState.AuthorizationRequired -> "Войдите в DeepSeek Harness и повторите подключение"
        is AiEngineState.Failed -> "Ошибка подключения"
    }
}
