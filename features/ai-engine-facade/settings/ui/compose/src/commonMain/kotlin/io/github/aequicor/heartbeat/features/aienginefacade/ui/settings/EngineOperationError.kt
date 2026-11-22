package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineOperationFailure
import kotlinx.coroutines.delay

@Composable
internal fun EngineErrorActions(failure: EngineOperationFailure, onRetry: () -> Unit) {
    var open by remember(failure) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
        SettingsButton("Повторить", onRetry, height = Layout.actionHeight)
        SettingsButton("Логи", { open = true }, height = Layout.actionHeight)
    }
    if (open) SettingsModal(Layout.journalWidth, { open = false }) { EngineJournal(failure) { open = false } }
}

private const val CopyFeedbackDurationMs = 2_000L

@Composable
@Suppress("DEPRECATION", "UnnecessaryLaunchedEffect") // Focus is requested once for this dialog instance.
internal fun EngineJournal(failure: EngineOperationFailure, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableIntStateOf(0) }
    var copyFailed by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied > 0) {
            delay(CopyFeedbackDurationMs)
            copied = 0
        }
    }
    Column(Modifier.fillMaxWidth().padding(HbSpacing.L), verticalArrangement = Arrangement.spacedBy(HbSpacing.M)) {
        SettingsText(failure.logTitle, style = HbTypography.Title)
        val shape = RoundedCornerShape(Aurora.SmallRadius)
        SelectionContainer(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().background(HbTheme.colors.surface.subtle, shape)
                .border(Aurora.Stroke, HbTheme.colors.border.subtle, shape).padding(Aurora.MessageGap),
                verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
                val mono = HbTypography.Code.copy(fontSize = HbTypography.Metadata.fontSize)
                failure.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
                        SettingsText(entry.time, Modifier.width(Layout.journalTimeWidth), mono, HbTheme
                            .colors.text.secondary)
                        val color = if (entry.level == EngineOperationFailure.Level.ERROR) HbTheme.colors
                            .status.error.foreground else HbTheme.colors.text.secondary
                        SettingsText(entry.level.name, Modifier.width(Layout.journalLevelWidth), mono, color)
                        SettingsText(entry.text, Modifier.weight(1f), mono)
                    }
                }
            }
        }
        SettingsRule()
        if (copyFailed) SettingsText("Не удалось скопировать логи", color = HbTheme.colors.status.error.foreground)
        val focus = remember { FocusRequester() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SettingsButton(if (copied > 0) "Логи скопированы" else "Скопировать", {
                try {
                    clipboard.setText(AnnotatedString(failure.logText))
                    copied++
                    copyFailed = false
                } catch (_: Exception) {
                    copied = 0
                    copyFailed = true
                }
            })
            SettingsButton("Закрыть", onClose, Modifier.focusRequester(focus))
        }
        LaunchedEffect(focus) { focus.requestFocus() }
    }
}
