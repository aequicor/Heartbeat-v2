package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbDialog
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbProgress
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState

@Composable
internal fun AuroraSecurityMode(
    state: AiStudioScreenState,
    chat: AiStudioScreenState.Chat,
    onAction: (AiStudioAction) -> Unit,
    touch: Boolean,
) {
    val bootstrap = state.sessionBootstrap?.takeIf { it.id == chat.id }?.picker
    val securityMode = (bootstrap?.securityMode ?: chat.securityMode) ?: return
    val busy = bootstrap?.securityModeBusy == true || chat.id in state.applyingSecurityModeFor
    val error = bootstrap?.securityModeError ?: state.securityModeErrors[chat.id]
    val idle = bootstrap != null || state.activity == AiStudioScreenState.Activity.Idle
    var submittedFromDialog by remember(chat.id) { mutableStateOf(false) }
    val required = securityMode.requiresUserChoice || error != null || submittedFromDialog && busy
    LaunchedEffect(required) { if (!required) submittedFromDialog = false }
    var history by remember(chat.id, required) { mutableStateOf(false) }
    val choose: (String?) -> Unit = {
        submittedFromDialog = required
        onAction(AiStudioAction.SelectSecurityMode(chat.id, it))
    }
    val label = securityMode.options.firstOrNull { it.value == securityMode.selected }?.label ?: "Выбрать режим"
    val target = if (touch) Layout.TouchTarget else Layout.HeaderButton
    val radius = if (touch) Layout.MessageRadius else Layout.FloatingHeaderRadius
    val actions = buildList {
        var group: String? = null
        for (option in securityMode.options) {
            if (option.group != group) {
                group = option.group
                option.group?.let { add(HbMenuAction(it, {}, enabled = false)) }
            }
            add(HbMenuAction(option.label, { choose(option.value) }, enabled = idle && !busy, selected = option.value == securityMode.selected))
        }
    }
    AuroraHeaderMenu(actions, touch = touch) { trigger, open ->
        val surface = if (touch) trigger.auroraHeaderSurface(radius)
        else trigger.auroraFloatingHeaderSurface(Colors.HeaderAccessSurface, Colors.HeaderAccessOutline)
        AuroraHeaderButton(
            "Режим доступа, $label", { if (required) history = false else open() }, surface,
            height = target, radius = radius, enabled = idle && !busy,
            contentPadding = PaddingValues(horizontal = Layout.ContentInset),
        ) {
            if (busy) HbProgress(Modifier.size(Layout.Glyph), label = "Применение режима")
            else HbIcon(HbIconGlyph.Shield, tint = Colors.Ink, size = Layout.Glyph)
            Spacer(Modifier.width(Layout.Space))
            AuroraHeaderText(label, Modifier.widthIn(max = Layout.ParameterMaxWidth))
            HbIcon(HbIconGlyph.ChevronDown, tint = Colors.Muted, size = Layout.SmallGlyph)
        }
    }
    if (required && idle && !history) {
        HbDialog(
            title = "Выберите режим доступа",
            onDismissRequest = {},
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            actions = {
                AuroraHeaderButton("К истории", { history = true }) { AuroraHeaderText("К истории") }
            },
        ) {
            if (error != null) AuroraHeaderText(error, color = Colors.Error)
            if (securityMode.options.isEmpty()) AuroraHeaderText("Агент не предоставил доступные режимы")
            var group: String? = null
            for (option in securityMode.options) {
                if (option.group != group) {
                    group = option.group
                    option.group?.let { AuroraHeaderText(it) }
                }
                AuroraHeaderButton(option.label, { choose(option.value) }, Modifier.fillMaxWidth(), enabled = !busy) {
                    Column {
                        AuroraHeaderText(option.label)
                        option.description?.let { AuroraHeaderText(it, color = Colors.Muted) }
                    }
                }
            }
            if (error != null || securityMode.options.isEmpty()) {
                AuroraHeaderButton("Повторить", { choose(null) }, enabled = !busy) { AuroraHeaderText("Повторить") }
            }
            if (busy) HbProgress(label = "Применение режима")
        }
    }
}
