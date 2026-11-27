package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_message_file
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

internal val LocalAttachmentFolderOpener = staticCompositionLocalOf<AttachmentFolderOpener?> { null }

@Composable
internal fun AuroraMessageAttachment(
    file: File,
    onAction: (AiStudioAction) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    measureTimestampTail: Boolean = false,
) {
    val issue = messageAttachmentSourceIssue(file)
    val folders = LocalAttachmentFolderOpener.current?.takeIf { it.isAvailable && file.canDeferReading && issue == null }
    val scope = rememberCoroutineScope()
    var folderError by remember(file.openId, file.originalUri, file.managedCopyPath) { mutableStateOf(false) }
    val isImage = file.image != null || file.mimeType?.startsWith("image/", ignoreCase = true) == true
    val description = file.description?.takeIf { !isImage && it.isNotBlank() }
    val measureInfo = measureTimestampTail && !folderError && description == null
    Column(modifier.width(AuroraLayout.MessageAttachmentWidth), verticalArrangement = Arrangement.spacedBy(AuroraLayout.MessageAttachmentGap)) {
        if (issue?.blocksPreview(file) == true) {
            AuroraUnavailableFileRow(file.name, issue)
        } else AuroraAttachmentFileMenu(file.openId, touch = false, onAction) { trigger, _ ->
            Row(
                trigger.fillMaxWidth().heightIn(min = AuroraLayout.MessageAttachmentTile),
                horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shape = RoundedCornerShape(
                    if (file.image != null) AuroraLayout.MessageAttachmentImageRadius else AuroraLayout.SmallRadius,
                )
                val open = if (file.canPreview) {
                    Modifier.semantics { contentDescription = "Открыть ${file.name}" }
                        .clickable(role = Role.Button, onClick = onOpen)
                } else Modifier.semantics { contentDescription = file.name }.focusable()
                val tile = if (file.image == null) Modifier.background(AuroraColors.FilePaneTile) else Modifier
                Box(
                    Modifier.size(AuroraLayout.MessageAttachmentTile).clip(shape)
                        .then(tile).then(open),
                    contentAlignment = Alignment.Center,
                ) {
                    AuroraAttachmentThumbnail(file.image, Modifier.fillMaxSize()) {
                        Image(painterResource(Res.drawable.attachment_message_file), null, Modifier.size(AuroraLayout.LargeGlyph))
                    }
                }
                val metadataTail = if (measureInfo) Modifier.attachmentTimestampTail() else Modifier
                Column(
                    metadataTail.weight(1f, fill = !measureInfo),
                    verticalArrangement = Arrangement.spacedBy(AuroraLayout.FrameStroke),
                ) {
                    BasicText(
                        file.name,
                        style = HbTypography.Label.copy(
                            color = AuroraColors.DesktopInk,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val metadata = HbTypography.Metadata.copy(color = AuroraColors.FileLocation)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
                        BasicText(file.sizeLabel, style = metadata)
                        if (folders != null) {
                            var focused by remember { mutableStateOf(false) }
                            BasicText("·", style = metadata)
                            BasicText(
                                "Показать в папке",
                                Modifier.onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button) {
                                    scope.launch {
                                        folderError = folders.reveal(file.originalUri, file.managedCopyPath).isFailure
                                    }
                                },
                                style = metadata.copy(textDecoration = TextDecoration.Underline.takeIf { focused }),
                            )
                        }
                    }
                }
            }
        }
        if (folderError) {
            AuroraText("Не удалось показать файл в папке", small = true, color = AuroraColors.Error)
        }
        if (description != null) {
            val style = HbTypography.Caption.copy(color = AuroraColors.DesktopInk)
            if (measureTimestampTail) AuroraTimestampParagraph(AnnotatedString(description), Modifier, style)
            else BasicText(description, style = style)
        }
    }
}
