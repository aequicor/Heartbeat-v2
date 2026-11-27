@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aequicor.heartbeat.designsystem.components.HbAppName
import io.github.aequicor.heartbeat.designsystem.components.HbBrandLogo
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbTabs
import io.github.aequicor.heartbeat.designsystem.components.HbDesktopFocusIndication
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowClientArea
import io.github.aequicor.heartbeat.features.aistudio.component.ProjectsSessionsComponent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileGroup
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.return_to_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.search_sessions
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraChats(
    state: AiStudioScreenState,
    touch: Boolean,
    avatarVisible: Boolean,
    onAction: (AiStudioAction) -> Unit,
    onOpenSettings: () -> Unit,
    projectsSessions: ProjectsSessionsComponent,
    modifier: Modifier = Modifier,
    close: Boolean = false,
) {
    Column(
        modifier.fillMaxHeight().padding(end = AuroraLayout.ContentInset),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.ControlGap),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (close) {
                AuroraControl(
                    { onAction(AiStudioAction.CollapseChats) },
                    label = stringResource(Res.string.return_to_session),
                    touch = touch, background = Color.Transparent,
                ) {
                    HbIcon(HbIconGlyph.Close, tint = AuroraColors.Muted, size = AuroraLayout.Glyph)
                }
            }
            HbBrandLogo(Modifier.padding(start = AuroraLayout.ControlGap))
            Spacer(Modifier.weight(1f))
            AuroraControl(
                onOpenSettings,
                label = stringResource(Res.string.settings),
                touch = touch,
                background = Color.Transparent,
            ) {
                AiStudioIcon(AiStudioGlyph.Settings, Modifier.size(AuroraLayout.Glyph), AuroraColors.Muted)
            }
        }
        AuroraControl(
            { onAction(AiStudioAction.CreateSessionWithoutProject) },
            Modifier.fillMaxWidth(), touch = touch, round = false,
        ) {
            HbIcon(HbIconGlyph.Plus, tint = AuroraColors.Muted, size = AuroraLayout.Glyph)
            AuroraText(stringResource(Res.string.new_session), Modifier.padding(start = AuroraLayout.Space))
        }
        AuroraSearch(
            stringResource(Res.string.search_sessions),
            state.chatSearch,
            { onAction(AiStudioAction.SearchChats(it)) },
            touch,
        )
        AuroraSessionList(state, touch, onAction, projectsSessions, Modifier.weight(1f))
        if (avatarVisible) {
            AuroraControl(
                onOpenSettings,
                label = stringResource(Res.string.settings),
                touch = touch,
                background = Color.Transparent,
            ) {
                AiStudioIcon(AiStudioGlyph.User, Modifier.size(AuroraLayout.Glyph), AuroraColors.Muted)
            }
        }
    }
}

@Composable
internal fun AuroraFiles(
    state: AiStudioScreenState,
    touch: Boolean,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var category by remember(state.chat?.id) { mutableStateOf(FileCategory.All) }
    val groups = remember(state.fileGroups, category) {
        if (category == FileCategory.All) state.fileGroups else state.fileGroups.mapNotNull { group ->
            val files = group.files.filter { it.isMedia == (category == FileCategory.Media) }
            group.copy(files = files).takeIf { files.isNotEmpty() }
        }
    }
    val canvas = if (touch) AuroraColors.DesktopCanvas else AuroraColors.ViewerSurface
    Column(modifier.fillMaxHeight().background(canvas)) {
        AuroraFilesHeader(state.fileSearchVisible, touch, onAction)
        if (state.fileSearchVisible) {
            val focus = remember { FocusRequester() }
            Box(Modifier.fillMaxWidth().padding(horizontal = AuroraLayout.Space, vertical = AuroraLayout.ControlGap)) {
                AuroraSearch(
                    "Поиск по файлам", state.fileSearch, { onAction(AiStudioAction.SearchFiles(it)) }, touch,
                    inputFocus = focus,
                )
            }
            SideEffect(focus) { focus.requestFocus() }
        }
        if (!touch) {
            HbTabs(
                FileCategory.entries, category, { category = it },
                modifier = Modifier.fillMaxWidth().fileHeaderDivider().padding(horizontal = AuroraLayout.Space),
                tabLabel = FileCategory::label,
                selectedContentColor = AuroraColors.DesktopInk,
                unselectedContentColor = AuroraColors.Muted,
                indicatorColor = AuroraColors.DesktopPrimaryAction,
            )
        }
        if (groups.isEmpty()) {
            val empty = state.fileSearch.isBlank() && category == FileCategory.All
            Box(Modifier.weight(1f)) {
                AuroraText(
                    if (empty) "В диалоге пока нет файлов" else "Файлы не найдены",
                    Modifier.padding(horizontal = AuroraLayout.PaneGap, vertical = AuroraLayout.Space),
                    color = AuroraColors.Muted,
                )
            }
        } else {
            AuroraFileList(groups, touch, state.chat?.id, state.attachmentLoadFailures, onAction, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AuroraFileList(
    groups: List<FileGroup>,
    touch: Boolean,
    sessionId: String?,
    attachmentLoadFailures: Set<String>,
    onAction: (AiStudioAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupStyle = HbTypography.Label.copy(color = AuroraColors.DesktopInk)
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(AuroraLayout.Space)) {
        groups.forEach { group ->
            item(key = "file-group:${group.key}") {
                BasicText(
                    group.title,
                    Modifier.padding(top = AuroraLayout.Space, bottom = AuroraLayout.SmallGap).semantics { heading() },
                    groupStyle,
                )
            }
            items(group.files, key = { "file:${it.id}" }) { file ->
                val issue = attachmentFileIssue(file.originalUri, file.managedCopyPath, file.id in attachmentLoadFailures)
                if (issue != null) {
                    AuroraUnavailableFileRow(
                        file.name, issue,
                        Modifier.padding(horizontal = AuroraLayout.SmallGap)
                            .padding(bottom = AuroraLayout.SmallGap),
                        onRemove = sessionId?.takeIf { file.canRemove }?.let { id ->
                            { onAction(AiStudioAction.RemoveUnavailableFile(id, file.id)) }
                        },
                    )
                } else AuroraAttachmentFileMenu(file.id, touch, onAction) { trigger, openMenu ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AuroraFileListRow(file, touch, { onAction(AiStudioAction.OpenFile(file.id)) }, trigger.weight(1f))
                        if (!touch) {
                            AuroraHeaderButton(
                                "Действия файла ${file.name}", openMenu, Modifier.width(32.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp),
                            ) {
                                HbIcon(HbIconGlyph.More, tint = AuroraColors.Muted, size = 18.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraFileListRow(file: FileRow, touch: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val metadata = HbTypography.Metadata.copy(color = AuroraColors.DesktopWorkMuted)
    val tile = if (!touch && file.image != null) 52.dp else AuroraLayout.FileTile
    val height = if (touch) AuroraLayout.FileRow else if (file.image != null) 68.dp else 60.dp
    AuroraHeaderButton(
        file.accessibilityLabel, onOpen, modifier.fillMaxWidth(),
        height = height, radius = AuroraLayout.ControlGap,
        contentPadding = PaddingValues(start = if (touch) Dp.Hairline else AuroraLayout.Space),
    ) {
        Box(
            Modifier.size(tile)
                .background(
                    if (touch) AuroraColors.FileTypeSurface else AuroraColors.FilePaneTile,
                    RoundedCornerShape(AuroraLayout.ControlGap),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val extension = file.extension
            AuroraAttachmentThumbnail(file.image, Modifier.fillMaxSize()) {
                if (!touch || extension == null) {
                    HbIcon(HbIconGlyph.Document, tint = AuroraColors.DesktopWorkMuted, size = AuroraLayout.Glyph)
                } else {
                    BasicText(extension, style = metadata, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(
            Modifier.weight(1f).padding(
                start = AuroraLayout.Space,
                top = AuroraLayout.Space,
                bottom = AuroraLayout.Space,
            ),
            verticalArrangement = Arrangement.spacedBy(AuroraLayout.Stroke * 2),
        ) {
            BasicText(
                file.name, style = auroraLabelStyle(AuroraColors.DesktopInk),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            BasicText(file.size, style = metadata)
            if (touch) file.date?.let { BasicText(it, style = metadata) }
        }
    }
}

@Composable
private fun AuroraFilesHeader(searchVisible: Boolean, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    val height = if (touch) AuroraLayout.FileViewerHeader else 52.dp
    val target = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget
    val glyph = if (touch) AuroraLayout.RequestGlyph else AuroraLayout.Glyph
    val titleStyle = HbTypography.PaneTitle.copy(color = AuroraColors.DesktopInk)
    Row(
        Modifier.fillMaxWidth().heightIn(min = height)
            .fileHeaderDivider()
            .padding(start = if (touch) AuroraLayout.Space else 18.dp, end = AuroraLayout.Space),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
    ) {
        if (touch) {
            AuroraHeaderButton(
                "Вернуться к диалогу", { onAction(AiStudioAction.ToggleFiles(false)) },
                height = target, contentPadding = PaddingValues(horizontal = (target - glyph) / 2),
            ) {
                AiStudioIcon(AiStudioGlyph.Back, Modifier.size(glyph), AuroraColors.FileBackInk)
            }
        }
        BasicText("Файлы", Modifier.weight(1f), titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AuroraHeaderButton(
            if (searchVisible) "Закрыть поиск файлов" else "Поиск файлов",
            { onAction(AiStudioAction.ShowFileSearch(!searchVisible)) },
            height = target, contentPadding = PaddingValues(horizontal = (target - glyph) / 2),
        ) {
            HbIcon(if (searchVisible) HbIconGlyph.Close else HbIconGlyph.Search,
                tint = AuroraColors.DesktopWorkMuted, size = glyph)
        }
        if (!touch) {
            AuroraHeaderButton(
                "Свернуть панель файлов", { onAction(AiStudioAction.ToggleFiles(false)) },
                height = target, contentPadding = PaddingValues(horizontal = (target - glyph) / 2),
            ) {
                HbIcon(HbIconGlyph.Close, tint = AuroraColors.DesktopWorkMuted, size = glyph)
            }
        }
    }
}

@Composable
internal fun AuroraFileLabel(file: File, modifier: Modifier = Modifier, attachmentTile: Boolean = false) {
    val tileSize = if (file.image == null && !attachmentTile) AuroraLayout.FileTile else AuroraLayout.TouchTarget
    val tileRadius = if (file.image == null) AuroraLayout.SmallRadius else AuroraLayout.SmallGap
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(tileSize)
                .background(AuroraColors.Canvas, RoundedCornerShape(tileRadius)),
            contentAlignment = Alignment.Center,
        ) {
            AuroraAttachmentThumbnail(file.image, Modifier.fillMaxSize()) {
                AiStudioIcon(AiStudioGlyph.Prompts, Modifier.size(AuroraLayout.LargeGlyph), AuroraColors.Muted)
            }
        }
        Column(Modifier.padding(start = AuroraLayout.Space)) {
            AuroraText(file.name)
            AuroraText(file.kind, small = true, color = AuroraColors.Muted)
        }
    }
}

@Composable
internal fun AuroraAttachmentThumbnail(image: ImageAttachment?, modifier: Modifier, fallback: @Composable () -> Unit) {
    val preview = remember(image) {
        runCatching { image?.thumbnailBytes()?.decodeToImageBitmap() }.getOrNull()
    }
    if (preview == null) fallback() else {
        Image(preview, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = modifier.clip(RoundedCornerShape(AuroraLayout.SmallGap)))
    }
}

@Composable
internal fun AuroraViewer(
    file: File,
    touch: Boolean,
    previews: AuroraDiagramPreviews? = null,
    onSaveEditedImage: ((ByteArray) -> Unit)? = null,
    copyError: Boolean = false,
    onClose: () -> Unit,
) {
    AuroraPanelDialog(onClose) {
        key(file.id) {
            CompositionLocalProvider(LocalDiagramPreviews provides previews) {
                Column(
                    Modifier.fillMaxSize().background(AuroraColors.ViewerSurface),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val title = if (file.image == null) "${file.kind} · ${file.name}" else file.name
                    AuroraViewerHeader(title, touch, onClose)
                    if (file.image != null) {
                        if (onSaveEditedImage != null) AuroraEditImageButton(file, onSaveEditedImage, "Сохранить копию")
                        if (copyError) AuroraText("Не удалось сохранить копию изображения", color = AuroraColors.Error)
                        AuroraImageDescription(file.description)
                        AuroraImagePreview(file, Modifier.weight(1f).fillMaxWidth().padding(AuroraLayout.PaneGap))
                    } else {
                        AuroraTextPreview(file, touch, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuroraImageDescription(description: String?) {
    if (description.isNullOrBlank()) return
    BasicText(
        description,
        Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = AuroraLayout.PaneGap, vertical = AuroraLayout.ControlGap),
        HbTypography.Caption.copy(color = AuroraColors.DesktopInk),
    )
}

@Composable
private fun AuroraTextPreview(file: File, touch: Boolean, modifier: Modifier) {
    val text = remember(file) { file.previewText() }
    val markdown = file.markdownContent
    val monospaced = markdown == null && (file.isPatch || file.kind == "Код" || file.kind == "Таблица")
    val style = (if (monospaced) HbTypography.Code else HbTypography.Body).copy(color = AuroraColors.DesktopInk)
    val selection = rememberSelectionState()
    val inset = Modifier.padding(AuroraLayout.PaneGap)
    SelectionContainer(
        state = selection,
        modifier = modifier.widthIn(max = AuroraLayout.Composer).fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
        when {
            text == null -> AuroraText("Предпросмотр недоступен", inset)
            markdown == null -> BasicText(text, inset, style)
            else -> AuroraMarkdownContent(text, markdown, selection, style, touch, inset)
        }
    }
}

@Composable
@Suppress("InjectDispatcher") // Image decoding belongs to the viewer's lifetime.
internal fun AuroraImagePreview(file: File, modifier: Modifier) {
    val result by produceState<Result<ImageBitmap>?>(null, file.content) {
        value = withContext(Dispatchers.Default) { file.decodeAttachmentImage() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = result?.getOrNull()
        when {
            image != null -> Image(image, file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            result?.isFailure == true -> AuroraText("Не удалось открыть изображение")
        }
    }
}

private fun File.decodeAttachmentImage(): Result<ImageBitmap> =
    runCatching { decodeAttachmentPreview(requireNotNull(imageBytes()), requireNotNull(image)) }

internal expect fun decodeAttachmentPreview(bytes: ByteArray, image: ImageAttachment): ImageBitmap

@Composable
private fun AuroraViewerHeader(title: String, touch: Boolean, onClose: () -> Unit) {
    val caption = HbWindowCaption.current
    val inset = if (touch) AuroraLayout.ContentInset else AuroraLayout.PaneGap
    val closeTarget = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget
    val titleLeft = if (caption.leftInset > Dp.Hairline) caption.leftInset + inset else inset
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(AuroraLayout.FileViewerHeader)
                .fileHeaderDivider()
                .absolutePadding(left = titleLeft, right = inset),
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                title,
                Modifier.weight(1f),
                style = HbTypography.PaneTitle.copy(color = AuroraColors.DesktopInk),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HbWindowClientArea { clientArea ->
                AuroraControl(
                    onClose, clientArea, label = "Закрыть просмотр", touch = touch,
                    background = Color.Transparent, horizontalPadding = Dp.Hairline,
                ) {
                    Box(Modifier.size(closeTarget), contentAlignment = Alignment.Center) {
                        HbIcon(HbIconGlyph.Close, tint = AuroraColors.ViewerCloseInk, size = AuroraLayout.Glyph)
                    }
                }
            }
        }
    }
}

private fun Modifier.fileHeaderDivider() = drawBehind {
    drawLine(
        AuroraColors.ViewerDivider,
        Offset(0f, size.height),
        Offset(size.width, size.height),
        strokeWidth = AuroraLayout.Stroke.toPx(),
    )
}

@Composable
internal fun AuroraPanelDialog(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HbDesktopFocusIndication(Modifier.fillMaxSize()) {
            val caption = HbWindowCaption.current
            Column(Modifier.fillMaxSize().background(AuroraColors.Canvas)) {
                if (caption.rightInset > Dp.Hairline) {
                    Box(
                        Modifier.fillMaxWidth().height(caption.height).padding(horizontal = AuroraLayout.PaneGap),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(
                            HbAppName,
                            style = HbTypography.Metadata.copy(color = AuroraColors.DesktopWorkMuted),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun AuroraSearch(
    queryLabel: String,
    value: String,
    onChange: (String) -> Unit,
    touch: Boolean,
    inputFocus: FocusRequester? = null,
) {
    val focusModifier = if (inputFocus == null) Modifier else Modifier.focusRequester(inputFocus)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget)
            .background(AuroraColors.Pearl, RoundedCornerShape(AuroraLayout.SmallRadius))
            .padding(horizontal = AuroraLayout.Space),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HbIcon(HbIconGlyph.Search, tint = AuroraColors.Muted, size = AuroraLayout.Glyph)
        BasicTextField(
            value, onChange,
            Modifier.weight(1f).padding(start = AuroraLayout.Space)
                .then(focusModifier).semantics { contentDescription = queryLabel },
            singleLine = true, textStyle = auroraTextStyle(), cursorBrush = SolidColor(AuroraColors.Primary),
            decorationBox = { field ->
                if (value.isEmpty()) AuroraText(queryLabel, small = true, color = AuroraColors.Muted)
                field()
            },
        )
    }
}
