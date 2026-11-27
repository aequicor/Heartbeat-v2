package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.PromptReferences.Category
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.PromptReferences.Entry
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.prompt_reference_file
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.prompt_reference_markdown
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.prompt_reference_skill
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraPromptReferencePopup(
    caret: Rect,
    choices: List<Entry>,
    current: Entry?,
    onHighlight: (Entry) -> Unit,
    onSelect: (Entry) -> Unit,
    onDismiss: () -> Unit,
) {
    val gap = with(LocalDensity.current) { Layout.ControlGap.roundToPx() }
    val position = remember(caret, gap) { PromptReferencePopupPosition(caret, gap) }
    Popup(position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = false)) {
        val shape = RoundedCornerShape(Layout.ContentInset)
        val list = rememberLazyListState()
        val sections = remember(choices) { choices.groupBy(Entry::category) }
        LaunchedEffect(current, sections) {
            if (current == null) return@LaunchedEffect
            val preceding = sections.keys.takeWhile { it != current.category }
            val index = preceding.sumOf { sections.getValue(it).size + 1 } +
                sections.getValue(current.category).indexOf(current) + 1
            if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
        }
        LazyColumn(
            Modifier.width(Layout.PromptReferenceWidth).heightIn(max = Layout.PromptReferenceMaxHeight)
                .dropShadow(shape, Shadow(
                    radius = Layout.PromptReferenceShadowRadius,
                    color = Colors.PromptReferenceShadow,
                    offset = DpOffset(Dp.Hairline, Layout.Space),
                ))
                .background(Colors.DesktopCanvas, shape)
                .border(Layout.Stroke, Colors.PromptReferenceOutline, shape)
                .clip(shape)
                .padding(horizontal = Layout.Space, vertical = Layout.Space + Layout.Stroke),
            state = list,
        ) {
            if (sections.isEmpty()) {
                item {
                    BasicText(
                        "Совпадений нет",
                        Modifier.padding(Layout.SmallGap),
                        HbTypography.Label.copy(color = Colors.Muted),
                    )
                }
            }
            for ((category, entries) in sections) {
                item(key = category) {
                    BasicText(
                        category.title(),
                        Modifier.padding(start = Layout.SmallGap, top = Layout.FrameStroke, bottom = Layout.SmallGap)
                            .semantics { heading() },
                        HbTypography.WorkspaceLabel.copy(color = Colors.Muted, fontWeight = FontWeight.Medium),
                    )
                }
                items(entries, key = { "${it.category}:${it.uri}" }) { entry ->
                    PromptReferenceRow(entry, entry == current, { onHighlight(entry) }, { onSelect(entry) })
                }
            }
        }
    }
}

@Composable
private fun PromptReferenceRow(entry: Entry, active: Boolean, onHover: () -> Unit, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val shape = RoundedCornerShape(Layout.PromptReferenceRowRadius)
    val muted = HbTypography.CompactFileMetadata.copy(color = Colors.Muted)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Layout.TouchTarget)
            .clip(shape)
            .then(if (active) Modifier.background(Colors.PromptReferenceSelected) else Modifier)
            .focusProperties { canFocus = false }
            .hoverable(interaction)
            .semantics { selected = active }
            .clickable(role = Role.Button, interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(horizontal = Layout.Space),
        horizontalArrangement = Arrangement.spacedBy(Layout.ContentInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Layout.PromptReferenceTile), contentAlignment = Alignment.Center) {
            AuroraAttachmentThumbnail(entry.image, Modifier.fillMaxSize().clip(RoundedCornerShape(Layout.ControlGap))) {
                val icon = when {
                    entry.category == Category.Skill -> Res.drawable.prompt_reference_skill
                    entry.name.endsWith(".md", ignoreCase = true) -> Res.drawable.prompt_reference_markdown
                    else -> Res.drawable.prompt_reference_file
                }
                Image(painterResource(icon), null, Modifier.size(Layout.PromptReferenceIcon))
            }
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                entry.name, style = HbTypography.Label.copy(color = Colors.DesktopInk, fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val detail = if (entry.category == Category.Skill) {
                listOfNotNull("Скил", entry.detail).joinToString(" · ")
            } else entry.detail
            detail?.let { BasicText(it, style = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (active) {
            BasicText(
                "↵", Modifier.padding(end = Layout.Space).clearAndSetSemantics {},
                HbTypography.Detail.copy(color = Colors.Muted),
            )
        }
    }
}

private fun Category.title(): String = when (this) {
    Category.SessionFile -> "ФАЙЛЫ СЕССИИ"
    Category.ProjectFile -> "ФАЙЛЫ ПРОЕКТА"
    Category.Skill -> "СКИЛЫ"
}
