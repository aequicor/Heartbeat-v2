package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraImageEditor as Editor
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing as Spacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraControl
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraText
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_center
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_zoom_in
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_zoom_out
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_brush
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_close
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_crop
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_mark
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_redo
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_rotate
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_save
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_shapes
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_text
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_undo
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_rectangle
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_ellipse
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_arrow
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.image_editor_ratio
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ImageEditorHeader(
    file: File,
    size: IntSize?,
    tool: ImageEditorTool?,
    canSave: Boolean,
    onTool: (ImageEditorTool) -> Unit,
    onRotate: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = size != null,
    actions: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = Spacing.L)) {
        val compact = maxWidth < Editor.HeaderBreakpoint
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = if (compact) Editor.CompactHeaderHeight else Editor.HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Layout.SmallGap)) {
                    BasicText(
                        file.name, style = HbTypography.Label.copy(color = Colors.DesktopInk, fontSize = Editor.HeaderTitle,
                            fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (size != null) AuroraText("${size.width} × ${size.height} px · ${file.sizeLabel}",
                        color = Colors.DesktopWorkMuted, small = true, lines = 1)
                }
                if (!compact) ImageEditorTools(tool, editable, onTool, onRotate)
                Row(
                    if (compact) Modifier else Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Layout.ContentInset, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ImageEditorPrimary("Сохранить копию", onSave, enabled = canSave, icon = Res.drawable.image_editor_save)
                    actions()
                    ImageEditorIconButton("Закрыть редактор", Res.drawable.image_editor_close, onClose)
                }
            }
            if (compact) {
                Box(Modifier.align(Alignment.CenterHorizontally).horizontalScroll(rememberScrollState())) {
                    ImageEditorTools(tool, editable, onTool, onRotate)
                }
            }
        }
    }
}

@Composable
private fun ImageEditorTools(
    selected: ImageEditorTool?,
    enabled: Boolean,
    onTool: (ImageEditorTool) -> Unit,
    onRotate: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Layout.Space), verticalAlignment = Alignment.CenterVertically) {
        ImageEditorTool.entries.forEach { tool ->
            val icon = when (tool) {
                ImageEditorTool.Crop -> Res.drawable.image_editor_crop
                ImageEditorTool.Text -> Res.drawable.image_editor_text
                ImageEditorTool.Brush -> Res.drawable.image_editor_brush
                ImageEditorTool.Shapes -> Res.drawable.image_editor_shapes
                ImageEditorTool.Mark -> Res.drawable.image_editor_mark
            }
            ImageEditorIconButton(tool.label, icon, { onTool(tool) }, enabled = enabled, active = selected == tool)
        }
        HbTooltip("Повернуть на 90° по часовой стрелке") {
            AuroraControl(
                onRotate, Modifier.width(Editor.RotateWidth), label = "Повернуть на 90° по часовой стрелке", enabled = enabled,
                round = false, horizontalPadding = Layout.DraftAttachmentFileInset, background = Colors.ViewerSurface, hoverBackground = Colors.WorkHover,
            ) {
                ImageEditorIcon(Res.drawable.image_editor_rotate)
                Spacer(Modifier.width(Layout.TimerThickness))
                AuroraText("90°", small = true)
            }
        }
    }
}

@Composable
internal fun ImageEditorFooter(
    zoomPercent: Int,
    history: ImageEditHistory,
    tool: ImageEditorTool?,
    settings: ImageEditorToolSettings,
    canApplyCrop: Boolean,
    onSettings: (ImageEditorToolSettings) -> Unit,
    onZoom: (Float) -> Unit,
    onCenter: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onApplyCrop: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val navigationWidth = Editor.ToolSize * 5 + Editor.ZoomLabelWidth + Layout.SmallGap * 5
        val cropActionsWidth = Editor.CropTriggerWidth + Editor.CropApplyWidth + Layout.ContentInset
        val hasFieldLabels = tool == ImageEditorTool.Text || tool == ImageEditorTool.Brush || tool == ImageEditorTool.Shapes
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (hasFieldLabels) Editor.ToolSettingsHeight else Editor.FooterHeight)
                .horizontalScroll(rememberScrollState()).padding(horizontal = Editor.CanvasInset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Layout.SmallGap),
        ) {
            ImageEditorIconButton("Уменьшить", Res.drawable.attachment_viewer_zoom_out, { onZoom(0.8f) })
            Box(Modifier.width(Editor.ZoomLabelWidth), contentAlignment = Alignment.Center) { AuroraText("$zoomPercent%", small = true) }
            ImageEditorIconButton("Увеличить", Res.drawable.attachment_viewer_zoom_in, { onZoom(1.25f) })
            ImageEditorIconButton("Центрировать", Res.drawable.attachment_viewer_center, onCenter)
            ImageEditorIconButton("Отменить правку", Res.drawable.image_editor_undo, onUndo, enabled = history.canUndo)
            ImageEditorIconButton("Повторить правку", Res.drawable.image_editor_redo, onRedo, enabled = history.canRedo)
            if (tool != null && tool != ImageEditorTool.Mark) {
                val gap = if (tool == ImageEditorTool.Crop) {
                    ((availableWidth - cropActionsWidth) / 2 - Editor.CanvasInset - navigationWidth - Layout.SmallGap * 2)
                        .coerceAtLeast(Spacing.M)
                } else Spacing.M
                Spacer(Modifier.width(gap))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Layout.ContentInset),
                verticalAlignment = Alignment.Bottom,
            ) {
                when (tool) {
                    ImageEditorTool.Crop -> {
                        ImageEditorSelect("Пропорции кадра", settings.aspect, ImageCropAspect.entries, ImageCropAspect::label,
                            { onSettings(settings.copy(aspect = it)) }, Editor.CropTriggerWidth, menuTitle = "Пропорции кадра",
                            iconOnly = true, showSelection = true, leading = { ImageEditorRatioIcon(it) },
                        )
                        ImageEditorPrimary(
                            "Применить", onApplyCrop, enabled = canApplyCrop, surface = Colors.DesktopPrimaryAction,
                            modifier = Modifier.width(Editor.CropApplyWidth),
                        )
                    }
                    ImageEditorTool.Text -> {
                        ImageEditorSelect("Шрифт", settings.font, ImageEditorFont.entries, ImageEditorFont::label,
                            { onSettings(settings.copy(font = it)) }, Editor.ToolSettingFontWidth,
                            menuWidth = Editor.ToolFontMenuWidth,
                            optionStyle = { HbTypography.Label.copy(fontFamily = it.fontFamily(), fontSize = Editor.FormText) },
                        )
                        ImageEditorSelect("Размер", settings.textSize, textSizes, { "$it px" },
                            { onSettings(settings.copy(textSize = it)) }, Editor.ToolSettingSizeWidth)
                        ImageEditorColorSelect("Цвет", settings.textColor, { if (it != null) onSettings(settings.copy(textColor = it)) })
                    }
                    ImageEditorTool.Brush -> {
                        ImageEditorColorSelect("Цвет", settings.brushColor, { if (it != null) onSettings(settings.copy(brushColor = it)) })
                        ImageEditorSelect("Толщина", settings.brushWidth, lineWidths, { "$it px" },
                            { onSettings(settings.copy(brushWidth = it)) }, Editor.ToolSettingThicknessWidth,
                            optionLeading = { ImageEditorStrokeSample(it) },
                        )
                    }
                    ImageEditorTool.Shapes -> {
                        ImageEditorSelect("Тип фигуры", settings.shape, ImageEditAnnotation.Shape.Kind.entries,
                            ImageEditAnnotation.Shape.Kind::label, { onSettings(settings.copy(shape = it)) }, Editor.ToolSettingShapeWidth,
                            menuWidth = Editor.ToolShapeMenuWidth, showSelection = true,
                            leading = { shape ->
                                val icon = when (shape) {
                                    ImageEditAnnotation.Shape.Kind.Rectangle -> Res.drawable.image_editor_rectangle
                                    ImageEditAnnotation.Shape.Kind.Ellipse -> Res.drawable.image_editor_ellipse
                                    ImageEditAnnotation.Shape.Kind.Arrow -> Res.drawable.image_editor_arrow
                                }
                                Box(Modifier.size(Layout.Glyph), contentAlignment = Alignment.Center) { ImageEditorIcon(icon) }
                            },
                        )
                        val arrow = settings.shape == ImageEditAnnotation.Shape.Kind.Arrow
                        ImageEditorColorSelect(if (arrow) "Цвет линии" else "Обводка", settings.shapeColor,
                            { if (it != null) onSettings(settings.copy(shapeColor = it)) })
                        ImageEditorSelect("Толщина", settings.shapeWidth, lineWidths, { "$it px" },
                            { onSettings(settings.copy(shapeWidth = it)) }, Editor.ToolSettingThicknessWidth,
                            optionLeading = { ImageEditorStrokeSample(it) },
                        )
                        if (!arrow) ImageEditorColorSelect("Заливка", settings.fill, { onSettings(settings.copy(fill = it)) }, nullable = true)
                    }
                    ImageEditorTool.Mark, null -> Unit
                }
            }
        }
    }
}

@Composable
private fun ImageEditorColorSelect(
    title: String, color: ImageEditorColor?, onChange: (ImageEditorColor?) -> Unit, nullable: Boolean = false,
) {
    ImageEditorSelect(title, color, (if (nullable) listOf(null) else emptyList()) + ImageEditorColor.entries,
        { it?.label ?: "Без заливки" }, onChange,
        if (nullable) Editor.ToolSettingFillWidth else Editor.ToolSettingColorWidth,
    ) { item ->
        Box(Modifier.size(Editor.Swatch).background(item?.color ?: Editor.NoFill, CircleShape)
            .border(Layout.Stroke, Colors.Line, CircleShape))
    }
}

@Composable
internal fun <T> ImageEditorSelect(
    title: String,
    value: T,
    choices: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    width: Dp,
    menuTitle: String? = null,
    iconOnly: Boolean = false,
    menuWidth: Dp = Editor.ToolValueMenuWidth,
    showSelection: Boolean = false,
    optionStyle: (@Composable (T) -> TextStyle)? = null,
    optionLeading: (@Composable (T) -> Unit)? = null,
    leading: @Composable (T) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.width(width)) {
        if (menuTitle == null) BasicText(
            title, Modifier.height(Editor.ToolSettingLabelHeight),
            HbTypography.Label.copy(color = Colors.DesktopWorkMuted, fontSize = Editor.FormLabel, lineHeight = Editor.FormText),
        )
        HbMenu(
            expanded = open,
            onDismissRequest = { open = false },
            actions = choices.map { option ->
                val styledLabel: (@Composable () -> TextStyle)? = if (optionStyle == null) null else {
                    { optionStyle(option) }
                }
                HbMenuAction(
                    label(option), { onSelect(option) },
                    selected = if (showSelection) option == value else null,
                    labelStyle = styledLabel,
                    leadingIcon = { if (optionLeading != null) optionLeading(option) else leading(option) },
                )
            },
            presentation = if (menuTitle == null) HbMenuPresentation.AuroraImageEditor else HbMenuPresentation.AuroraImageCrop,
            title = menuTitle,
            popupWidth = if (menuTitle == null) menuWidth else Editor.CropMenuWidth,
        ) { modifier ->
            AuroraControl(
                { open = !open },
                modifier.fillMaxWidth().height(Layout.DesktopTarget).border(
                    Layout.Stroke, Editor.ToolMenuOutline, RoundedCornerShape(Editor.FormFieldRadius),
                ),
                label = "$title · ${label(value)}",
                minimumTarget = Layout.DesktopTarget, round = false,
                background = Colors.ViewerSurface, hoverBackground = Colors.WorkHover,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Layout.Space), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.then(if (iconOnly) Modifier.weight(1f) else Modifier), contentAlignment = Alignment.Center) {
                        leading(value)
                    }
                    if (!iconOnly) BasicText(
                        label(value), Modifier.weight(1f),
                        HbTypography.Label.copy(color = Colors.DesktopInk, fontSize = Editor.ToolSettingText),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    HbIcon(HbIconGlyph.ChevronDown, size = Layout.SmallGlyph)
                }
            }
        }
    }
}

@Composable
private fun ImageEditorStrokeSample(thickness: Int) {
    Box(Modifier.size(Spacing.Xl, thickness.dp).background(Colors.DesktopInk, CircleShape))
}

@Composable
private fun ImageEditorRatioIcon(aspect: ImageCropAspect) {
    val outlineSize = when (aspect) {
        ImageCropAspect.Free -> null
        ImageCropAspect.Square -> Editor.SquareRatioSize
        ImageCropAspect.Standard -> Editor.StandardRatioSize
        ImageCropAspect.Photo -> Editor.PhotoRatioSize
        ImageCropAspect.Landscape -> Editor.LandscapeRatioSize
        ImageCropAspect.Portrait -> Editor.PortraitRatioSize
    }
    Box(Modifier.size(Layout.Glyph), contentAlignment = Alignment.Center) {
        if (outlineSize == null) {
            ImageEditorIcon(Res.drawable.image_editor_ratio)
        } else {
            Box(Modifier.size(outlineSize.first, outlineSize.second).border(
                Editor.RatioOutline, Editor.FormFieldLine, RoundedCornerShape(Layout.FrameStroke),
            ))
        }
    }
}

@Composable
internal fun ImageEditorPrimary(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: DrawableResource? = null,
    surface: Color = Editor.ActionSurface,
    modifier: Modifier = Modifier,
) {
    AuroraControl(
        onClick, modifier.height(Editor.ToolSize), enabled = enabled, label = label,
        background = if (enabled) surface else Colors.WorkHover, round = false,
    ) {
        if (icon != null) {
            ImageEditorIcon(icon, ColorFilter.tint(if (enabled) Colors.DesktopOnPrimaryAction else Colors.DesktopWorkMuted))
            Spacer(Modifier.width(Layout.Space))
        }
        Box(if (icon == null) Modifier.fillMaxWidth() else Modifier, contentAlignment = Alignment.Center) {
            AuroraText(label, color = if (enabled) Colors.DesktopOnPrimaryAction else Colors.DesktopWorkMuted, small = true)
        }
    }
}

@Composable
private fun ImageEditorIconButton(
    label: String, icon: DrawableResource, onClick: () -> Unit, enabled: Boolean = true, active: Boolean = false,
) {
    HbTooltip(label) {
        AuroraControl(
            onClick, Modifier.size(Editor.ToolSize).semantics { selected = active },
            label = label, enabled = enabled, round = false, horizontalPadding = Spacing.None,
            background = if (active) Editor.ToolMenuSelection else Colors.ViewerSurface,
            hoverBackground = if (active) null else Colors.WorkHover,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ImageEditorIcon(icon, modifier = Modifier.alpha(if (enabled) 1f else 0.35f))
                if (active) Box(Modifier.align(Alignment.BottomCenter).padding(top = Editor.SelectedToolTop)
                    .width(Editor.SelectedToolWidth).height(Layout.FrameStroke).background(Colors.DesktopPrimaryAction))
            }
        }
    }
}

@Composable
private fun ImageEditorIcon(icon: DrawableResource, tint: ColorFilter? = null, modifier: Modifier = Modifier) {
    Image(painterResource(icon), null, modifier, colorFilter = tint)
}

private val textSizes = listOf(16, 20, 24, 32, 48)
private val lineWidths = listOf(1, 2, 3, 5, 8, 12)
