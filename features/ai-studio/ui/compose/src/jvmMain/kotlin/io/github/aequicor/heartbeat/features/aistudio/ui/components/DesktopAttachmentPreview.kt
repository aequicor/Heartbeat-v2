package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraImageEditor as Editor
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing as Spacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor.ImageEditResult
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_close
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_more
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_search
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.attachment_viewer_wrap
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraDesktopAttachmentPreview(
    file: File,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actionError: String? = null,
    onSaveEditedImage: ((ImageEditResult) -> Unit)? = null,
) {
    if (file.image != null) {
        ContextMenuArea(items = { listOf(ContextMenuItem("Скопировать", onCopy)) }) {
            AuroraAttachmentImageEditor(
                file = file,
                onClose = onClose,
                onApply = onSaveEditedImage,
                modifier = modifier,
                actionError = actionError,
                actions = { AttachmentPreviewActions(onCopy, onSave) },
            )
        }
        return
    }
    var searchVisible by remember(file.openId) { mutableStateOf(false) }
    var query by remember(file.openId) { mutableStateOf("") }
    var activeHit by remember(file.openId) { mutableIntStateOf(0) }
    var wrap by remember(file.openId) { mutableStateOf(false) }
    var sourceMode by remember(file.openId) { mutableStateOf(false) }
    val document = rememberAttachmentDocument(file, sourceMode)
    val hits = rememberAttachmentHits(document, query.takeIf { searchVisible }.orEmpty())
    val hit = hits.getOrNull(activeHit)
    val closeFocus = remember { FocusRequester() }
    val searchButtonFocus = remember { FocusRequester() }

    fun moveHit(direction: Int) {
        if (hits.isNotEmpty()) activeHit = (activeHit + direction).mod(hits.size)
    }

    fun closeSearch() {
        searchVisible = false
        searchButtonFocus.requestFocus()
    }

    LaunchedEffect(file.openId) { closeFocus.requestFocus() }
    Column(
        modifier.fillMaxSize().background(Colors.ViewerSurface).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                event.key == Key.Escape -> {
                    if (searchVisible) closeSearch() else onClose()
                    true
                }
                file.canPreview && event.key == Key.F && (event.isCtrlPressed || event.isMetaPressed) -> {
                    searchVisible = true
                    true
                }
                else -> false
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().height(Layout.FileViewerHeader).background(Colors.ViewerToolbar)
                .padding(start = Spacing.M, end = Layout.Space),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                file.name, Modifier.weight(1f), viewerToolbarStyle(),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (file.canPreview) {
                ViewerButton(
                    "Поиск", Res.drawable.attachment_viewer_search,
                    onClick = { if (searchVisible) closeSearch() else searchVisible = true },
                    modifier = Modifier.focusRequester(searchButtonFocus),
                    selected = searchVisible,
                )
                Spacer(Modifier.width(Layout.Space))
                if (file.isMarkdown) {
                    val label = if (sourceMode) "Отформатированный текст" else "Исходный текст"
                    HbTooltip(label) {
                        AuroraHeaderButton(label, {
                            sourceMode = !sourceMode
                            activeHit = 0
                        }, fill = selectedBackground(sourceMode)) {
                            HbIcon(if (sourceMode) HbIconGlyph.Eye else HbIconGlyph.Document, size = Layout.Glyph)
                        }
                    }
                    Spacer(Modifier.width(Layout.Space))
                }
                if (!file.isMarkdown || sourceMode) {
                    ViewerButton("Перенос строк", Res.drawable.attachment_viewer_wrap, { wrap = !wrap }, selected = wrap)
                    Spacer(Modifier.width(Layout.Space))
                }
            }
            AttachmentPreviewActions(onCopy, onSave)
            ViewerButton(
                "Закрыть просмотр", Res.drawable.attachment_viewer_close, onClose, Modifier.focusRequester(closeFocus),
            )
        }
        if (actionError != null) {
            AuroraText(actionError, Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Layout.Space), color = Colors.Error)
        }
        if (searchVisible) {
            AttachmentSearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    activeHit = 0
                },
                counter = "${if (hit == null) 0 else activeHit + 1} / ${hits.size}",
                hasResults = hits.isNotEmpty(),
                onMove = ::moveHit,
                onClose = ::closeSearch,
            )
        }
        AttachmentTextViewport(document, file.canPreview, wrap, hit, query.length, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
internal fun AttachmentPreviewActions(onCopy: () -> Unit, onSave: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    HbMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        alignEnd = true,
        presentation = HbMenuPresentation.AuroraFileActions,
        actions = attachmentFileActions(onCopy, onSave),
    ) { modifier ->
        AuroraHeaderButton("Действия файла", { expanded = !expanded }, modifier, verticalInset = Dp.Hairline) {
            Box(Modifier.size(Layout.DesktopTarget), contentAlignment = Alignment.Center) {
                ViewerIcon(Res.drawable.attachment_viewer_more)
            }
        }
    }
}

@Composable
private fun AttachmentSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    counter: String,
    hasResults: Boolean,
    onMove: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().background(Colors.ViewerToolbar).padding(horizontal = Spacing.M, vertical = Layout.SmallGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Layout.Space),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = HbTypography.Label.copy(color = Colors.DesktopInk),
            modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Найти в файле" }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) return@onPreviewKeyEvent false
                    onMove(if (event.isShiftPressed) -1 else 1)
                    true
                },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().background(Colors.ViewerSurface).padding(horizontal = Layout.ChromeInset, vertical = Layout.ControlGap)) {
                    if (query.isEmpty()) BasicText("Найти в файле", style = HbTypography.Label.copy(color = Colors.DesktopWorkMuted))
                    inner()
                }
            },
        )
        BasicText(counter, style = viewerToolbarStyle())
        AuroraHeaderButton("Предыдущее совпадение", { onMove(-1) }, enabled = hasResults) {
            HbIcon(HbIconGlyph.ChevronDown, Modifier.rotate(180f), size = Layout.Glyph)
        }
        AuroraHeaderButton("Следующее совпадение", { onMove(1) }, enabled = hasResults) {
            HbIcon(HbIconGlyph.ChevronDown, size = Layout.Glyph)
        }
        ViewerButton("Закрыть поиск", Res.drawable.attachment_viewer_close, onClose)
    }
}

@Composable
private fun ViewerButton(
    label: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    glyphSize: Dp = Layout.Glyph,
    enabled: Boolean = true,
) {
    HbTooltip(label, enabled = enabled) {
        val state = if (selected != null) Modifier.semantics { this.selected = selected } else Modifier
        AuroraHeaderButton(
            label, onClick, modifier.then(state), verticalInset = Dp.Hairline,
            enabled = enabled, fill = selectedBackground(selected == true),
        ) {
            Box(Modifier.size(Layout.DesktopTarget), contentAlignment = Alignment.Center) {
                ViewerIcon(icon, glyphSize)
            }
        }
    }
}

@Composable
private fun ViewerIcon(icon: DrawableResource, size: Dp = Layout.Glyph) {
    Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(size))
}

@Composable
private fun viewerToolbarStyle() =
    HbTypography.Metadata.copy(color = Colors.DesktopWorkMuted, fontSize = Editor.ViewerMetadataSize, lineHeight = Editor.ViewerMetadataLine)

private fun selectedBackground(selected: Boolean) =
    if (selected) Colors.NavigationHover else Editor.NoFill
