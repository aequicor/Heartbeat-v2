package io.github.aequicor.heartbeat.features.aistudio.ui.components

import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditResult
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditorFooterDescription
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageEditor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.DraftAttachmentImageLoader
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AttachmentImportSource
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_dialog_check
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_dialog_close
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_dialog_file
import org.jetbrains.compose.resources.painterResource
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication

internal val LocalDraftAttachmentImageLoader = staticCompositionLocalOf<DraftAttachmentImageLoader?> { null }

@Composable
internal fun AuroraAttachmentDialog(draftId: String, file: File, onAction: (AiStudioAction) -> Unit, error: String? = null) {
    val loaded = rememberDraftAttachmentOriginal(file, LocalDraftAttachmentImageLoader.current)
    var edited by remember(file.id) { mutableStateOf<ImageEditResult?>(null) }
    val preview = remember(edited, loaded) { edited?.preview(file) ?: loaded?.getOrNull() }
    val editSource = attachmentImageEditSource()
    val dismiss = { onAction(AiStudioAction.CancelAttachment(draftId, file.id)) }
    AttachmentDetailsDialog(dismiss) {
        AuroraAttachmentDetails(
            file = if (edited == null) file else checkNotNull(preview),
            onDismiss = dismiss,
            editedImageRequiresLink = edited != null && preview?.exceedsInlineImageLimit == true,
            onConfirm = { name, description, deferred ->
                onAction(AiStudioAction.ConfirmAttachment(
                    draftId, file.id, name, description, deferred, edited?.png?.let(editSource), edited?.thumbnail,
                ))
            },
        ) {
            AttachmentDetailsPreview(if (edited == null) file else checkNotNull(preview), preview, onImageEdited = { edited = it })
            if (error != null) AuroraText(error, small = true, color = AuroraColors.Error)
            if (loaded?.isFailure == true) {
                BasicText("Не удалось загрузить оригинал", style = fieldLabelStyle(12))
            }
        }
    }
}

@Composable
internal fun AuroraDraftAttachmentEditor(
    file: File,
    onDismiss: () -> Unit,
    onSave: (String, String?, Boolean, AttachmentImportSource?, String?) -> Unit,
) {
    val loader = LocalDraftAttachmentImageLoader.current
    val canLoadImage = file.image != null && loader != null
    val loaded = rememberDraftAttachmentOriginal(file, loader)
    val original = loaded?.getOrNull()
    var edited by remember(file.id) { mutableStateOf<ImageEditResult?>(null) }
    val preview = remember(edited, original) { edited?.preview(file) ?: original }
    val editSource = attachmentImageEditSource()
    AttachmentDetailsDialog(onDismiss) {
        val currentFile = if (edited == null) file else checkNotNull(preview)
        AuroraAttachmentDetails(
            currentFile,
            onDismiss,
            { name, description, deferred -> onSave(name, description, deferred, edited?.png?.let(editSource), edited?.thumbnail) },
            title = "Редактирование вложения",
            confirmLabel = "Сохранить",
            maxWidth = if (file.image != null) 560.dp else 400.dp,
            editedImageRequiresLink = edited != null && preview?.exceedsInlineImageLimit == true,
        ) {
            AttachmentDetailsPreview(
                currentFile, preview, height = 240.dp, onImageEdited = { edited = it },
            )
            if (preview == null && canLoadImage) {
                val label = if (loaded == null) "Загружаем оригинал…" else "Не удалось загрузить оригинал"
                BasicText(label, style = fieldLabelStyle(12))
            }
        }
    }
}

@Composable
private fun rememberDraftAttachmentOriginal(file: File, loader: DraftAttachmentImageLoader?): Result<File>? {
    if (file.image == null || loader == null) return null
    return key(file, loader) {
        val loaded by produceState<Result<File>?>(null) {
            value = loader.load(file)
        }
        loaded
    }
}

@Composable
private fun AttachmentDetailsDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f))
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
                .padding(AuroraLayout.Space),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) { content() }
        }
    }
}

@Composable
internal fun AuroraAttachmentDetails(
    file: File,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = if (file.image != null) "Добавить изображение" else "Добавить файл",
    confirmLabel: String = "Добавить",
    maxWidth: Dp = 400.dp,
    editedImageRequiresLink: Boolean = false,
    preview: @Composable () -> Unit = { AttachmentDetailsPreview(file) },
) {
    var name by rememberSaveable(file.id, file.name) { mutableStateOf(file.name) }
    val imageDescription = remember(file.description, file.image) {
        if (file.image != null) ImageEditor.fromDescription(file.description)
        else ImageEditor(file.description.orEmpty(), emptyList())
    }
    var description by rememberSaveable(file.id, file.description) { mutableStateOf(imageDescription.text) }
    var deferred by key(file.id, file.deferredReading) {
        rememberSaveable { mutableStateOf(file.deferredReading) }
    }
    val shape = RoundedCornerShape(12.dp)
    val background = AuroraColors.DesktopCanvas
    Column(
        modifier.widthIn(max = maxWidth).fillMaxWidth(1f)
            .dropShadow(shape, Shadow(14.dp, Color(0x30140F1F), offset = DpOffset(0.dp, 8.dp)))
            .clip(shape).background(background).border(AuroraLayout.Stroke, AuroraColors.Line, shape)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                title,
                Modifier.weight(1f).semantics { heading() },
                HbTypography.Label.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = AuroraColors.Ink),
            )
            AuroraControl(
                onDismiss, Modifier.size(24.dp),
                label = "Закрыть", round = false, horizontalPadding = 0.dp, minimumTarget = 24.dp,
                background = background, hoverBackground = AuroraColors.WorkHover,
            ) {
                Image(painterResource(Res.drawable.attachment_dialog_close), null, Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        preview()
        Spacer(Modifier.height(12.dp))
        AttachmentNameField(name, { name = it })
        if (file.hasInvalidImageExtension(name)) {
            BasicText(
                "Расширение должно соответствовать формату изображения",
                style = detailTextStyle(AuroraColors.FileIssueInk),
            )
        }
        Spacer(Modifier.height(12.dp))
        AttachmentDescriptionField(description, { description = it })
        ImageEditorFooterDescription(imageDescription.footer())
        Spacer(Modifier.height(12.dp))
        AttachmentDeferCheckbox(deferred || editedImageRequiresLink, file.canDeferReading && !editedImageRequiresLink) {
            deferred = it
        }
        if (editedImageRequiresLink) {
            AuroraText("Копия больше 1 МиБ и будет отправлена ссылкой", small = true, color = AuroraColors.Muted)
        }
        Spacer(Modifier.height(25.dp))
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space)) {
            val border = RoundedCornerShape(AuroraLayout.SmallRadius)
            AuroraControl(
                onDismiss, Modifier.width(92.dp).height(36.dp).border(AuroraLayout.Stroke, AuroraColors.HeaderAccessOutline, border),
                round = false, horizontalPadding = 0.dp, background = background, hoverBackground = AuroraColors.WorkHover,
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText("Отмена", style = detailTextStyle(AuroraColors.DesktopPrimaryAction, FontWeight.Medium))
                }
            }
            val canSubmit = name.isNotBlank() && name.none(Char::isISOControl)
            AuroraControl(
                { onConfirm(name.trim(), imageDescription.copy(text = description).description(), (deferred || editedImageRequiresLink) && file.canDeferReading) },
                Modifier.width(96.dp).height(36.dp), enabled = canSubmit, round = false, horizontalPadding = 0.dp,
                background = if (canSubmit) AuroraColors.DesktopPrimaryAction else AuroraColors.Lavender,
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText(confirmLabel, style = detailTextStyle(
                        if (canSubmit) AuroraColors.DesktopOnPrimaryAction else AuroraColors.Muted, FontWeight.Medium,
                    ))
                }
            }
        }
    }
}

@Composable
private fun AttachmentDetailsPreview(
    file: File,
    currentImage: File? = null,
    height: Dp = 154.dp,
    onImageEdited: ((ImageEditResult) -> Unit)? = null,
) {
    val detail = attachmentTypeAndSize(file)
    if (file.image != null) {
        val surface = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)).background(AuroraColors.Canvas)
        if (currentImage != null && onImageEdited != null) {
            AuroraEditableImagePreview(currentImage, onApply = onImageEdited, modifier = surface)
        } else {
            Box(surface) {
                AuroraAttachmentThumbnail(file.image, Modifier.fillMaxSize()) {
                    Image(painterResource(Res.drawable.attachment_dialog_file), null, Modifier.align(Alignment.Center).size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(detail, style = fieldLabelStyle(12))
    } else {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .background(Color(0xFFF5F3F7), RoundedCornerShape(10.dp)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(AuroraColors.Canvas, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.attachment_dialog_file), null, Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(file.name, style = detailTextStyle(weight = FontWeight.Medium),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(detail, style = fieldLabelStyle(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AttachmentNameField(value: String, onValueChange: (String) -> Unit) {
    val label = "Имя файла"
    var focused by remember { mutableStateOf(false) }
    val border = if (focused) AuroraColors.DesktopPrimaryAction else AuroraColors.Muted
    Column(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(label, style = fieldLabelStyle(11, FontWeight.Medium).copy(color = border))
        BasicTextField(
            value, onValueChange,
            Modifier.fillMaxWidth().heightIn(min = 23.dp).onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            singleLine = true, textStyle = detailTextStyle(), cursorBrush = SolidColor(AuroraColors.DesktopPrimaryAction),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(border))
    }
}

@Composable
private fun AttachmentDescriptionField(value: String, onValueChange: (String) -> Unit) {
    val label = "Описание (необязательно)"
    var focused by remember { mutableStateOf(false) }
    val showLabel = focused || value.isNotEmpty()
    val borderColor = if (focused) AuroraColors.DesktopPrimaryAction else AuroraColors.Muted
    val borderWidth = if (focused) 2.dp else 1.dp
    BasicTextField(
        value, onValueChange,
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        textStyle = detailTextStyle(), cursorBrush = SolidColor(AuroraColors.DesktopPrimaryAction),
        decorationBox = { input ->
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 7.dp).heightIn(min = 65.dp)
                        .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (!showLabel) BasicText(label, style = detailTextStyle(AuroraColors.Muted))
                    input()
                }
                if (showLabel) BasicText(
                    label,
                    Modifier.offset(x = 8.dp).background(AuroraColors.DesktopCanvas).padding(horizontal = 4.dp),
                    fieldLabelStyle(11, FontWeight.Medium).copy(color = borderColor),
                )
            }
        },
    )
}

@Composable
private fun AttachmentDeferCheckbox(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val primary = AuroraColors.DesktopPrimaryAction
    val shape = RoundedCornerShape(4.dp)
    var focused by remember { mutableStateOf(false) }
    val focusVisible = focused && HbFocusIndication.keyboardNavigation
    val focusOutline = if (focusVisible) Modifier.border(AuroraLayout.Stroke, primary, shape) else Modifier
    Row(
        Modifier.fillMaxWidth().heightIn(min = 22.dp).then(focusOutline)
            .onFocusChanged { focused = it.isFocused }
            .toggleable(
                checked, interactionSource = null, indication = null,
                enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ink = if (enabled) AuroraColors.Ink else AuroraColors.Muted
        Box(
            Modifier.size(18.dp).clip(shape).background(if (checked) primary else AuroraColors.DesktopCanvas)
                .border(1.dp, if (checked) primary else AuroraColors.Muted, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Image(painterResource(Res.drawable.attachment_dialog_check), null, Modifier.size(16.dp))
        }
        BasicText("Отложенное чтение", style = detailTextStyle(ink, FontWeight.Medium))
    }
}

@Composable
private fun detailTextStyle(color: Color = AuroraColors.Ink, weight: FontWeight = FontWeight.Normal) =
    HbTypography.Label.copy(color = color, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = weight)

@Composable
private fun fieldLabelStyle(size: Int, weight: FontWeight = FontWeight.Normal) =
    HbTypography.Label.copy(color = AuroraColors.Muted, fontSize = size.sp, lineHeight = (size + 2).sp, fontWeight = weight)

private fun attachmentTypeAndSize(file: File): String {
    val type = when (val extension = file.name.substringAfterLast('.', "").lowercase()) {
        "ts", "tsx" -> "TypeScript"
        else -> extension.uppercase().ifBlank { file.mimeType.orEmpty() }
    }
    return listOfNotNull(type.takeIf(String::isNotEmpty), file.sizeLabel).joinToString(" · ")
}
