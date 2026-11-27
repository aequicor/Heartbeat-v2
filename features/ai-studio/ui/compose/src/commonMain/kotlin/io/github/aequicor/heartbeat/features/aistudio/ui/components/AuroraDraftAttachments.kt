package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_unavailable
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.draft_attachment_file
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.draft_attachment_remove
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraDraftAttachments(
    files: List<File>, touch: Boolean, onEdit: (File) -> Unit, onAction: (AiStudioAction) -> Unit,
) {
    if (touch) {
        Column(verticalArrangement = Arrangement.spacedBy(Layout.SmallGap)) {
            files.forEach { file -> key(file.id) { DraftAttachment(file, touch, { onEdit(file) }, onAction) } }
        }
    } else {
        Row(
            Modifier.padding(horizontal = Layout.FrameStroke + Layout.Stroke).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Layout.Space),
        ) {
            files.forEach { file -> key(file.id) { DraftAttachment(file, touch, { onEdit(file) }, onAction) } }
        }
    }
}

@Composable
private fun DraftAttachment(file: File, touch: Boolean, onEdit: () -> Unit, onAction: (AiStudioAction) -> Unit) {
    val image = file.image
    val invalid = file.hasInvalidImageExtension()
    val shape = RoundedCornerShape(Layout.SmallRadius)
    val sizeLabel = remember(file) { file.sizeLabel }
    val target = if (touch) Layout.TouchTarget else Layout.DesktopTarget
    val width = when {
        touch -> Modifier.fillMaxWidth()
        image != null -> Modifier.width(Layout.DraftAttachmentImageWidth)
        else -> Modifier.width(Layout.DraftAttachmentFileWidth)
    }
    val surface = if (invalid) Colors.FileIssueSurface else Colors.DraftAttachmentSurface
    val tile = if (invalid) Colors.FileIssueTile else Colors.FilePaneTile
    val nameColor = if (invalid) Colors.FileIssueInk else Colors.DesktopInk
    val detailColor = if (invalid) Colors.FileIssueInk else Colors.Muted
    Box(width.heightIn(min = Layout.DraftAttachmentHeight).background(surface, shape)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Layout.DraftAttachmentHeight).clip(shape)
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics { contentDescription = "Редактировать ${file.name}" }
                .clickable(role = Role.Button, onClick = onEdit).padding(
                start = if (image != null) Layout.ControlGap else Layout.DraftAttachmentFileInset,
                end = if (touch) target else Layout.DraftAttachmentRemoveSpace,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Layout.Space),
        ) {
            Box(
                Modifier.size(if (image != null) Layout.DraftAttachmentImageTile else Layout.FileTile)
                    .clip(shape).background(tile),
                contentAlignment = Alignment.Center,
            ) {
                if (invalid) {
                    Image(painterResource(Res.drawable.attachment_unavailable), null, Modifier.size(Layout.RequestGlyph))
                } else AuroraAttachmentThumbnail(image, Modifier.fillMaxSize()) {
                    Image(painterResource(Res.drawable.draft_attachment_file), null, Modifier.size(Layout.LargeGlyph))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (image != null) Layout.SmallGap else Layout.FrameStroke)) {
                val title = if (image != null) HbTypography.Metadata else HbTypography.Caption
                BasicText(file.name, style = title.copy(color = nameColor, fontWeight = FontWeight.Medium),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = if (invalid) AttachmentFileIssue.LoadFailed.label else sizeLabel
                BasicText(detail, style = HbTypography.CompactFileMetadata.copy(color = detailColor))
            }
        }
        AuroraControl(
            { onAction(AiStudioAction.Detach(file.id)) },
            Modifier.align(Alignment.TopEnd).size(target),
            label = "Убрать ${file.name}", touch = touch, background = Color.Transparent,
            round = false, horizontalPadding = Dp.Hairline,
            hoverBackground = Colors.PromptReferenceSelected,
        ) {
            Box(Modifier.fillMaxSize().padding(top = Layout.DraftAttachmentRemoveTop, end = Layout.ControlGap),
                contentAlignment = if (touch) Alignment.Center else Alignment.TopEnd) {
                Image(painterResource(Res.drawable.draft_attachment_remove), null, Modifier.size(Layout.MenuGlyph))
            }
        }
    }
}
