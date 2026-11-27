package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraImageEditor as Editor
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing as Spacing
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageEditor
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageEditor.Mark
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraAttachmentModal
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraControl
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraText
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_lock
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.ibm_plex_mono
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
internal fun ImageEditorMarkForm(
    anchor: Offset,
    mark: Mark,
    editing: Boolean,
    onSave: (Mark) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    readOnly: Boolean = false,
    displayedPixel: IntOffset = IntOffset(mark.x, mark.y),
) {
    var name by remember(mark) { mutableStateOf(mark.name) }
    var description by remember(mark) { mutableStateOf(mark.description) }
    var measuredHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Editor.Scrim)
            .clickable(interactionSource = null, indication = null, onClick = onCancel))
        val cardWidth = (maxWidth - Editor.FormMargin).coerceIn(Layout.Stroke, Editor.FormWidth)
        val width = with(density) { cardWidth.toPx() }
        val height = measuredHeight.toFloat()
        val hostWidth = constraints.maxWidth.toFloat()
        val hostHeight = constraints.maxHeight.toFloat()
        val inset = with(density) { Layout.Space.toPx() }
        val point = Offset(anchor.x.coerceIn(0f, hostWidth), anchor.y.coerceIn(0f, hostHeight))
        val arrow = with(density) { Editor.MarkArrow.toPx() }
        val arrowInset = with(density) { Editor.MarkArrowInset.toPx() }
        val halfArrowWidth = with(density) { Editor.MarkArrowHalfWidth.toPx() }
        val toRight = point.x + width - arrowInset <= hostWidth - inset
        val below = point.y + height + arrow <= hostHeight - inset || point.y < hostHeight / 2
        val left = (if (toRight) point.x - arrowInset else point.x - width + arrowInset)
            .coerceIn(inset, (hostWidth - width - inset).coerceAtLeast(inset))
        val top = (if (below) point.y + arrow else point.y - height - arrow)
            .coerceIn(inset, (hostHeight - height - inset).coerceAtLeast(inset))
        Canvas(Modifier.fillMaxSize()) {
            val edge = if (below) top + Layout.Stroke.toPx() else top + height - Layout.Stroke.toPx()
            val center = point.x.coerceIn(left + arrowInset, (left + width - arrowInset).coerceAtLeast(left + arrowInset))
            drawPath(Path().apply {
                moveTo(point.x, point.y)
                lineTo(center - halfArrowWidth, edge)
                lineTo(center + halfArrowWidth, edge)
                close()
            }, Colors.ViewerSurface)
        }
        Column(
            Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(cardWidth).heightIn(max = (maxHeight - Editor.FormMargin).coerceAtLeast(Layout.Stroke))
                .onSizeChanged { measuredHeight = it.height }
                .imageEditorFormSurface()
                .verticalScroll(rememberScrollState()).imageEditorFormPadding(),
        ) {
            ImageEditorFormHeader(
                if (readOnly) mark.name else "Добавить метку",
                onCancel,
                closeLabel = if (readOnly) "Закрыть" else "Отмена",
                height = Editor.MarkHeaderHeight,
            )
            ImageEditorFormMetadata("X ${displayedPixel.x}    Y ${displayedPixel.y} px")
            Spacer(Modifier.height(Layout.Space))
            if (readOnly) {
                if (mark.description.isNotBlank()) AuroraText(mark.description)
            } else {
                ImageEditorSingleLineField("Название", name, { name = it })
                Spacer(Modifier.height(Spacing.M - Layout.Stroke * 2))
                ImageEditorDescriptionField("Описание (необязательно)", description, { description = it })
                Spacer(Modifier.height(Editor.FormActionGap))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (editing) AuroraControl(
                        onDelete, label = "Удалить метку", background = Colors.ViewerSurface,
                        horizontalPadding = Spacing.None, round = false,
                    ) {
                        AuroraText("Удалить", color = Colors.FileIssueInk, small = true)
                    }
                    Spacer(Modifier.weight(1f))
                    ImageEditorFormAction(
                        "Сохранить", { onSave(mark.copy(name = name.trim(), description = description)) },
                        enabled = name.isNotBlank(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ImageEditorSaveForm(
    original: ImageBitmap,
    document: ImageEditDocument,
    defaultName: String,
    status: String?,
    exporting: Boolean,
    saveAvailable: Boolean,
    largeDraft: Boolean,
    longSide: String,
    onLongSideChange: (String) -> Unit,
    quality: ImageEditorPngQuality,
    onQualityChange: (ImageEditorPngQuality) -> Unit,
    outputSize: IntSize,
    previewImage: ImageBitmap?,
    outputBytes: Int?,
    outputDescription: ImageEditor,
    onMarks: (ImageEditor) -> Unit,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    var text by remember { mutableStateOf(document.description.text) }
    var selectedMark by remember { mutableStateOf<Int?>(null) }
    var previewAnchor by remember { mutableStateOf<Offset?>(null) }
    var hostPosition by remember { mutableStateOf(Offset.Zero) }
    var previewPosition by remember { mutableStateOf(Offset.Zero) }
    val state = document.description
    val index = selectedMark
    val mark = index?.let(state.marks::getOrNull)
    AuroraAttachmentModal(onDismiss = { if (mark == null) onCancel() else selectedMark = null }) {
        Box(Modifier.fillMaxSize().padding(Layout.ContentInset).onGloballyPositioned { hostPosition = it.positionInRoot() }, contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = Editor.FormWidth).fillMaxWidth().imageEditorFormSurface()
                    .pointerInput(Unit) { detectTapGestures { } }.verticalScroll(rememberScrollState()).imageEditorFormPadding(),
            ) {
                ImageEditorFormHeader("Сохранить копию", onCancel)
                Spacer(Modifier.height(Layout.SmallGap))
                ImageEditorSavePreview(
                    original, document, previewImage, outputSize, outputDescription.marks,
                    Modifier.fillMaxWidth().height(Editor.FormPreviewHeight)
                        .clip(RoundedCornerShape(Layout.SmallRadius))
                        .onGloballyPositioned { previewPosition = it.positionInRoot() },
                ) { selected, position ->
                    selectedMark = selected
                    previewAnchor = position
                }
                Spacer(Modifier.height(Layout.Space))
                val outputWeight = outputBytes?.let { " · ${(it + 1023) / 1024} КиБ" }.orEmpty()
                ImageEditorFormMetadata("PNG · ${outputSize.width} × ${outputSize.height}$outputWeight")
                Spacer(Modifier.height(Layout.ContentInset))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Layout.ContentInset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        ImageEditorSingleLineField(
                            "Длинная сторона, px", longSide, onLongSideChange,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    ImageEditorSelect(
                        "Качество", quality, ImageEditorPngQuality.entries, ImageEditorPngQuality::label,
                        onQualityChange, width = Editor.ToolSettingColorWidth, showSelection = true,
                    )
                }
                Spacer(Modifier.height(Layout.ContentInset))
                ImageEditorSingleLineField("Имя файла", name, { name = it }, suffix = ".png")
                Spacer(Modifier.height(Layout.ContentInset))
                val hasMarks = state.marks.isNotEmpty()
                ImageEditorDescriptionField(
                    if (hasMarks) "Описание" else "Описание (необязательно)",
                    text,
                    { text = it },
                    placeholder = if (hasMarks) "Добавить описание…" else null,
                    footer = outputDescription.footer(),
                )
                Spacer(Modifier.height(Editor.FormActionGap))
                if (largeDraft) AuroraText("Копия больше 1 МиБ и будет отправлена ссылкой", small = true, color = Colors.DesktopWorkMuted)
                status?.let { AuroraText(it, small = true, color = Colors.FileIssueInk) }
                ImageEditorSaveButtonRow(name, text, exporting, saveAvailable && mark == null, onSave)
            }
            val anchor = previewAnchor
            if (mark != null && anchor != null) {
                ImageEditorMarkForm(
                    previewPosition - hostPosition + anchor, mark, editing = true,
                    onSave = { edited ->
                        onMarks(state.copy(marks = state.marks.mapIndexed { i, current -> if (i == index) edited else current }))
                        selectedMark = null
                    },
                    onDelete = {
                        onMarks(state.copy(marks = state.marks.filterIndexed { i, _ -> i != index }))
                        selectedMark = null
                    },
                    onCancel = { selectedMark = null },
                    displayedPixel = outputDescription.marks[index].let { IntOffset(it.x, it.y) },
                )
            }
        }
    }
}

@Composable
private fun ImageEditorSaveButtonRow(
    name: String, text: String, exporting: Boolean, saveAvailable: Boolean, onSave: (String, String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        val valid = name.isNotBlank() && name.none { it.isISOControl() || it == '/' || it == '\\' }
        ImageEditorFormAction(
            if (exporting) "Сохранение…" else "Сохранить", { onSave(name.trim(), text) },
            enabled = valid && saveAvailable,
        )
    }
}

@Composable
internal fun ImageEditorFooterDescription(
    footer: String,
    showDivider: Boolean = true,
    textColor: Color = Colors.DesktopWorkMuted,
    textSize: TextUnit = Editor.FormMetadata,
) {
    if (footer.isEmpty()) return
    if (showDivider) Box(Modifier.fillMaxWidth().padding(vertical = Layout.Space).height(Layout.Stroke).background(Editor.FormDivider))
    Row(horizontalArrangement = Arrangement.spacedBy(Layout.ControlGap)) {
        SelectionContainer(Modifier.weight(1f)) {
            BasicText(footer, style = HbTypography.Code.copy(
                color = textColor, fontFamily = FontFamily(Font(Res.font.ibm_plex_mono)), fontSize = textSize,
            ))
        }
        Image(painterResource(Res.drawable.image_editor_lock), "Метки только для чтения", Modifier.size(Layout.SmallGlyph))
    }
}

@Composable
private fun ImageEditorSavePreview(
    original: ImageBitmap,
    document: ImageEditDocument,
    preview: ImageBitmap?,
    target: IntSize,
    marks: List<Mark>,
    modifier: Modifier,
    onMark: (Int, Offset) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val bounds = ImageEditorViewport().bounds(size, target, density)
    Box(modifier.onSizeChanged { size = it }.semantics { contentDescription = "Предпросмотр копии" }) {
        Canvas(Modifier.fillMaxSize()) {
            if (bounds.width > 0f && preview != null) {
                drawImage(
                    preview,
                    dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
                    dstSize = IntSize(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)),
                )
            } else if (bounds.width > 0f) {
                drawImageEdit(
                    original, document, bounds.topLeft, bounds.width / document.size.width,
                    scaleY = bounds.height / document.size.height,
                )
            }
        }
        if (bounds.width > 0f) {
            marks.forEachIndexed { index, mark ->
                val point = bounds.pixelCenter(mark.x, mark.y, target)
                ImageMarkBadge(mark, index + 1, point) { onMark(index, point) }
            }
        }
    }
}

@Composable
internal fun ImageEditorTextForm(onCancel: () -> Unit, onApply: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                onCancel()
                true
            } else false
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Editor.Scrim)
            .clickable(interactionSource = null, indication = null, onClick = onCancel))
        Column(
            Modifier.widthIn(max = Editor.FormWidth).fillMaxWidth()
                .imageEditorFormSurface().imageEditorFormPadding(),
            verticalArrangement = Arrangement.spacedBy(Layout.ContentInset),
        ) {
            ImageEditorFormHeader("Добавить текст", onCancel)
            ImageEditorDescriptionField("Надпись", text, { text = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ImageEditorFormAction("Добавить", { onApply(text) }, enabled = text.isNotBlank())
            }
        }
    }
}

private fun Modifier.imageEditorFormSurface(): Modifier {
    val shape = RoundedCornerShape(Editor.FormRadius)
    return dropShadow(shape, Shadow(Editor.FormElevation, Editor.FormShadow, offset = DpOffset(Spacing.None, Editor.FormShadowOffset)))
        .background(Colors.ViewerSurface, shape)
        .border(Layout.Stroke, Colors.Line, shape)
}

private fun Modifier.imageEditorFormPadding(): Modifier = padding(
    start = Editor.FormInset,
    top = Editor.FormTopInset,
    end = Editor.FormInset,
    bottom = Editor.FormBottomInset,
)

@Composable
private fun ImageEditorFormHeader(
    title: String,
    onCancel: () -> Unit,
    closeLabel: String = "Отмена",
    height: Dp = Editor.FormHeaderHeight,
) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, Modifier.weight(1f), HbTypography.Label.copy(
            color = Colors.DesktopInk, fontWeight = FontWeight.SemiBold, fontSize = Editor.FormTitle,
        ), maxLines = 1, overflow = TextOverflow.Ellipsis)
        AuroraControl(
            onCancel, Modifier.width(Editor.FormCancelWidth), label = closeLabel,
            background = Colors.ViewerSurface, hoverBackground = Colors.WorkHover, round = false,
            horizontalPadding = Spacing.None,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BasicText(closeLabel, style = HbTypography.Label.copy(color = Colors.DesktopInk, fontSize = Editor.FormText))
            }
        }
    }
}

@Composable
private fun ImageEditorFormMetadata(text: String) {
    BasicText(text, Modifier.height(Spacing.M), HbTypography.Label.copy(
        color = Editor.FormFieldLine, fontSize = Editor.FormMetadata, lineHeight = Editor.FormMetadata,
    ))
}

@Composable
private fun ImageEditorFormAction(label: String, onClick: () -> Unit, enabled: Boolean) {
    AuroraControl(
        onClick, Modifier.height(Editor.ToolSize).widthIn(min = Editor.FormActionWidth),
        label = label, enabled = enabled, background = Editor.ActionSurface.copy(alpha = if (enabled) 1f else 0.4f),
        round = false,
    ) {
        Box(Modifier.widthIn(min = Editor.FormActionWidth - Layout.Space * 2), contentAlignment = Alignment.Center) {
            BasicText(label, style = HbTypography.Label.copy(
                color = Colors.ViewerSurface, fontSize = Editor.FormText, fontWeight = FontWeight.Medium,
            ))
        }
    }
}

@Composable
private fun ImageEditorSingleLineField(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(Modifier.fillMaxWidth()) {
        ImageEditorFieldLabel(title, Modifier.height(Editor.FormLabelHeight))
        Box(Modifier.fillMaxWidth().height(Editor.SingleLineFieldHeight)) {
            Row(
                Modifier.fillMaxSize().padding(bottom = Layout.Stroke),
                horizontalArrangement = Arrangement.spacedBy(Layout.Space),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImageEditorTextInput(
                    title, value, onChange, Modifier.weight(1f), singleLine = true, keyboardOptions = keyboardOptions,
                )
                suffix?.let {
                    BasicText(it, style = HbTypography.Label.copy(color = Editor.FormFieldLine, fontSize = Editor.FormText))
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Layout.Stroke).background(Editor.FormFieldLine))
        }
    }
}

@Composable
private fun ImageEditorDescriptionField(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    footer: String = "",
) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(top = Editor.DescriptionLabelOverlap)
                .heightIn(min = Editor.MultilineFieldHeight)
                .border(Layout.Stroke, Editor.FormFieldLine, RoundedCornerShape(Editor.FormFieldRadius))
                .padding(Layout.ContentInset),
        ) {
            ImageEditorTextInput(
                title, value, onChange, Modifier.fillMaxWidth()
                    .then(if (footer.isEmpty()) Modifier.heightIn(min = Editor.MultilineFieldHeight - Layout.ContentInset * 2) else Modifier),
                placeholder = placeholder,
            )
            if (footer.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = Layout.Space).height(Layout.Stroke).background(Editor.FormDivider))
                Box(Modifier.fillMaxWidth().heightIn(min = Editor.DescriptionPreviewHeight)) {
                    ImageEditorFooterDescription(footer, showDivider = false)
                }
            }
        }
        ImageEditorFieldLabel(title, Modifier.padding(start = Layout.Space)
            .background(Colors.ViewerSurface).padding(horizontal = Layout.SmallGap))
    }
}

@Composable
private fun ImageEditorFieldLabel(text: String, modifier: Modifier) {
    BasicText(text, modifier, HbTypography.Label.copy(
        color = Editor.FormFieldLine, fontSize = Editor.FormLabel, fontWeight = FontWeight.Medium,
        lineHeight = Editor.FormLabel,
    ))
}

@Composable
private fun ImageEditorTextInput(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    singleLine: Boolean = false,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val style = HbTypography.Label.copy(color = Colors.DesktopInk, fontSize = Editor.FormText, lineHeight = HbTypography.Label.lineHeight)
    BasicTextField(
        value, onChange, modifier.semantics { contentDescription = title },
        singleLine = singleLine, maxLines = if (singleLine) 1 else 5, keyboardOptions = keyboardOptions,
        textStyle = style, cursorBrush = SolidColor(Colors.DesktopPrimaryAction),
        decorationBox = { content ->
            Box {
                if (value.isEmpty() && placeholder != null) {
                    BasicText(placeholder, style = style.copy(color = Colors.DesktopWorkMuted))
                }
                content()
            }
        },
    )
}
