package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_remove
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_unavailable
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraUnavailableFileRow(
    name: String,
    issue: AttachmentFileIssue,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    Row(
        modifier.fillMaxWidth()
            .background(AuroraColors.FileIssueSurface, shape)
            .heightIn(min = AuroraLayout.FileIssueRow)
            .padding(start = AuroraLayout.Space, end = AuroraLayout.ControlGap),
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.ChromeInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(AuroraLayout.FileTile).background(AuroraColors.FileIssueTile, shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(Res.drawable.attachment_unavailable), null, Modifier.size(AuroraLayout.RequestGlyph))
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                name, style = HbTypography.Label.copy(color = AuroraColors.FileIssueInk, fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            BasicText(issue.label, style = HbTypography.Metadata.copy(color = AuroraColors.FileIssueInk))
        }
        if (onRemove != null) {
            AuroraHeaderButton(
                "Удалить запись о файле $name", onRemove, Modifier.size(AuroraLayout.DesktopTarget),
                radius = AuroraLayout.SmallRadius,
                fill = AuroraColors.FileIssueTile,
                contentPadding = PaddingValues(horizontal = AuroraLayout.ControlGap),
                verticalInset = Dp.Hairline,
            ) {
                Image(painterResource(Res.drawable.attachment_remove), null, Modifier.size(AuroraLayout.RequestGlyph))
            }
        }
    }
}
