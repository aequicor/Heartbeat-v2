package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

@Composable
@ReadOnlyComposable
internal fun auroraMessageStyle(): TextStyle = HbTypography.Body.copy(color = AuroraColors.MessageInk)

@Composable
@ReadOnlyComposable
internal fun auroraLabelStyle(color: Color): TextStyle = HbTypography.Label.copy(color = color)

@Composable
@ReadOnlyComposable
internal fun auroraTimestampStyle(): TextStyle = HbTypography.Metadata.copy(color = AuroraColors.MessageMuted)

internal enum class AuroraWorkRole {
    Action, StageTitle, Summary, Detail, Metadata,
}

@Composable
internal fun AuroraWorkText(
    text: String,
    role: AuroraWorkRole,
    modifier: Modifier = Modifier,
    color: Color = AuroraColors.WorkInk,
    maxLines: Int = Int.MAX_VALUE,
) {
    val style = when (role) {
        AuroraWorkRole.Action -> HbTypography.Label
        AuroraWorkRole.StageTitle -> HbTypography.PaneTitle
        AuroraWorkRole.Summary -> HbTypography.Caption
        AuroraWorkRole.Metadata -> HbTypography.Metadata
        AuroraWorkRole.Detail -> HbTypography.Detail
    }
    val ink = style.copy(color = color)
    if (replyTextRevealProvided()) {
        AuroraReplyText(
            text, modifier, ink, revealKey = remember { Any() }, maxLines = maxLines, overflow = TextOverflow.Ellipsis, cascade = false,
        )
    } else {
        BasicText(text, modifier, ink, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
