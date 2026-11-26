@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Quota

@Composable
internal fun AuroraHeaderMetrics(
    chat: Chat,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = chat.contextPercent
    val quota = chat.quota
    if (context == null && quota == null) return

    val details = auroraMetricDetails(context, quota)
    AuroraMetricMenu(chat, modifier, touch = false) { trigger, open ->
        val both = !compact && context != null && quota != null
        val width = if (both) {
            Modifier.widthIn(min = AuroraLayout.HeaderMetricWidth).width(IntrinsicSize.Max)
        } else {
            Modifier
        }
        AuroraHeaderButton(
            details.joinToString(", "), open,
            trigger.then(width).auroraFloatingHeaderSurface(AuroraColors.Pearl, AuroraColors.HeaderOutline),
            height = AuroraLayout.HeaderButton, radius = AuroraLayout.FloatingHeaderRadius,
            contentPadding = PaddingValues(horizontal = AuroraLayout.ContentInset),
        ) {
            if (!compact) {
                BasicText(
                    if (context == null) "Лимиты" else "Контекст",
                    style = HbTypography.Label.copy(color = AuroraColors.MessageMuted),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val gap = Modifier.width(AuroraLayout.HeaderClusterGap)
                Spacer(if (both) gap.weight(1f) else gap)
            }
            AuroraMetricValues(context, quota)
        }
    }
}

@Composable
internal fun AuroraTouchMetrics(chat: Chat, modifier: Modifier = Modifier) {
    val context = chat.contextPercent
    val quota = chat.quota
    if (context == null && quota == null) return
    val details = auroraMetricDetails(context, quota)
    val radius = AuroraLayout.MessageRadius
    AuroraMetricMenu(chat, modifier, touch = true) { trigger, open ->
        AuroraHeaderButton(
            details.joinToString(", "), open,
            trigger.widthIn(min = AuroraLayout.TouchHeaderMetricWidth).auroraHeaderSurface(radius),
            height = AuroraLayout.TouchTarget, radius = radius,
            contentPadding = PaddingValues(horizontal = AuroraLayout.MessageRadius),
        ) {
            if (context != null) AuroraContextPercent(context, touch = true)
            else if (quota != null) AuroraQuota(quota, touch = true)
        }
    }
}

private fun auroraMetricDetails(context: Int?, quota: Quota?) = listOfNotNull(
    context?.let { "Контекст · использовано $it%" },
    quota?.let { "Лимиты за ${it.hours} ч · использовано ${it.usedPercent}%" },
)

@Composable
private fun AuroraMetricValues(context: Int?, quota: Quota?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.ChromeInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (context != null) AuroraContextPercent(context)
        if (context != null && quota != null) {
            Box(Modifier.size(AuroraLayout.Stroke, AuroraLayout.HeaderRuleHeight)
                .background(AuroraColors.HeaderMetricTrack))
        }
        if (quota != null) AuroraQuota(quota)
    }
}

@Composable
private fun AuroraContextPercent(context: Int, touch: Boolean = false) {
    val accent = if (touch) AuroraColors.TouchHeaderMetricAccent else AuroraColors.HeaderMetricAccent
    Row(horizontalArrangement = Arrangement.spacedBy(AuroraLayout.ControlGap),
        verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(AuroraLayout.SmallGlyph)) {
            val width = (if (touch) AuroraLayout.WorkEventStroke else AuroraLayout.HeaderRingStroke).toPx()
            val ring = Stroke(width, cap = if (touch) StrokeCap.Round else StrokeCap.Butt)
            drawCircle(AuroraColors.HeaderMetricTrack, radius = (size.minDimension - width) / 2f, style = ring)
            inset(width / 2f) {
                drawArc(
                    accent, startAngle = -90f, sweepAngle = 360f * percent(context),
                    useCenter = false, style = ring,
                )
            }
        }
        AuroraMetricValue("$context%", if (touch) AuroraColors.Ink else AuroraColors.MessageInk)
    }
}

@Composable
private fun AuroraQuota(quota: Quota, touch: Boolean = false) {
    val accent = if (touch) AuroraColors.TouchHeaderMetricAccent else AuroraColors.HeaderMetricAccent
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
        AuroraMetricValue("${quota.hours} ч", if (touch) AuroraColors.Muted else AuroraColors.MessageMuted)
        Box(Modifier.size(AuroraLayout.HeaderQuotaTrack, AuroraLayout.HeaderQuotaHeight)
            .clip(RoundedCornerShape(AuroraLayout.FrameStroke)).background(AuroraColors.HeaderMetricTrack)) {
            Box(Modifier.fillMaxWidth(percent(quota.usedPercent)).fillMaxHeight()
                .clip(RoundedCornerShape(AuroraLayout.FrameStroke)).background(accent))
        }
    }
}

@Composable
private fun AuroraMetricValue(text: String, color: Color) {
    BasicText(
        text, style = HbTypography.Metadata.copy(color = color),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

private fun percent(value: Int): Float = value.coerceIn(0, 100) / 100f
