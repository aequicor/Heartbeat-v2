package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.rememberHbMenuPositionProvider
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ContextUsage
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Quota

@Composable
internal fun AuroraMetricMenu(
    chat: Chat,
    modifier: Modifier,
    touch: Boolean,
    trigger: @Composable (Modifier, () -> Unit) -> Unit,
) {
    var expanded by remember(chat.id) { mutableStateOf(false) }
    val anchor = remember { FocusRequester() }
    fun dismiss() {
        expanded = false
        anchor.requestFocus()
    }
    Box(modifier) {
        trigger(Modifier.focusRequester(anchor)) { expanded = true }
        if (expanded) Popup(
            popupPositionProvider = rememberHbMenuPositionProvider(alignEnd = true),
            onDismissRequest = ::dismiss,
            properties = PopupProperties(focusable = true),
        ) {
            MetricPanel(chat, touch, ::dismiss)
        }
    }
}

@Composable
private fun MetricPanel(chat: Chat, touch: Boolean, dismiss: () -> Unit) {
    val percent = chat.contextPercent
    val quota = chat.quota
    val layoutDirection = LocalLayoutDirection.current
    var showLimits by remember(percent != null, quota != null) { mutableStateOf(percent == null) }
    val selected = remember { FocusRequester() }
    LaunchedEffect(showLimits) {
        withFrameNanos { }
        selected.requestFocus()
    }
    val shape = RoundedCornerShape(Layout.ControlGap)
    Column(
        Modifier.width(Layout.MetricMenuWidth).heightIn(max = Layout.EngineMenuMaxHeight)
            .auroraMenuBackground(shape, Colors.Canvas).border(Layout.Stroke, Colors.HeaderOutline, shape)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> { dismiss(); true }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (percent == null || quota == null) return@onPreviewKeyEvent false
                        showLimits = (event.key == Key.DirectionRight) == (layoutDirection == LayoutDirection.Ltr)
                        true
                    }
                    else -> false
                }
            }
            .verticalScroll(rememberScrollState()).padding(Layout.Space),
    ) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Layout.SmallGap)) {
            MetricTab("Контекст", !showLimits, percent != null, selected, touch) { showLimits = false }
            MetricTab("Лимиты", showLimits, quota != null, selected, touch) { showLimits = true }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Layout.SmallGap, vertical = Layout.ContentInset),
            verticalArrangement = Arrangement.spacedBy(Layout.Space),
        ) {
            if (!showLimits && percent != null) ContextDetails(chat.contextUsage, percent)
            if (showLimits && quota != null) LimitDetails(quota)
        }
    }
}

@Composable
private fun RowScope.MetricTab(
    label: String, active: Boolean, enabled: Boolean, selected: FocusRequester, touch: Boolean, select: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Layout.ControlGap)
    Box(
        Modifier.weight(1f).heightIn(min = if (touch) Layout.TouchTarget else Layout.NavigationRow)
            .clip(shape)
            .then(if (active) Modifier.background(Colors.HeaderMetricTrack) else Modifier)
            .then(if (active) Modifier.focusRequester(selected) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused && HbFocusIndication.keyboardNavigation) Modifier.border(Layout.Stroke, Colors.Primary, shape) else Modifier)
            .selectable(active, enabled = enabled, role = Role.Tab, onClick = select)
            .padding(Layout.SmallGap),
        contentAlignment = Alignment.Center,
    ) {
        AuroraHeaderText(label, color = if (enabled) Colors.Ink else Colors.Muted, style = HbTypography.Metadata)
    }
}

@Composable
private fun ContextDetails(usage: ContextUsage?, percent: Int) {
    val label = if (usage == null) "Занято" else "Занято ${tokenCount(usage.used)} / ${tokenCount(usage.size)}"
    MetricProgress(label, percent, usage?.fraction?.toFloat() ?: (percent / 100f))
    if (usage != null) {
        val remaining = usage.remaining
        val unit = when {
            remaining % 100 in 11L..14L -> "токенов"
            remaining % 10 == 1L -> "токен"
            remaining % 10 in 2L..4L -> "токена"
            else -> "токенов"
        }
        AuroraHeaderText("Свободно ${tokenCount(remaining)} $unit", color = Colors.Muted, style = HbTypography.Metadata)
    }
}

@Composable
private fun LimitDetails(quota: Quota) {
    MetricProgress("Использовано за ${quota.hours} ч", quota.usedPercent, quota.usedPercent / 100f)
}

@Composable
private fun MetricProgress(label: String, percent: Int, fraction: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = HbTypography.Metadata.copy(color = Colors.Ink))
        AuroraHeaderText("$percent%", Modifier.padding(start = Layout.Space), style = HbTypography.Metadata)
    }
    val progress = fraction.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(Layout.HeaderQuotaHeight).clip(RoundedCornerShape(Layout.SmallGap))
            .background(Colors.HeaderMetricTrack)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Colors.HeaderMetricAccent, RoundedCornerShape(Layout.SmallGap)))
    }
}

private fun tokenCount(count: Long): String = count.toString().reversed().chunked(3).joinToString(" ").reversed()
