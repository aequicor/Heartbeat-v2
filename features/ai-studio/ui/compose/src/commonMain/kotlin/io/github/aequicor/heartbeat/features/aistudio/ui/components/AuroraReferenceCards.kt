package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyBlock
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraMessageReference
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraMessageReference.Fragment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.LocalPromptLink
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AuroraMessageReference.Format
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.chat_reference_close
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.chat_reference_file
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.chat_reference_quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraReplyReference(
    reference: AuroraMessageReference,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().referenceAccent(reference.accent).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "К источнику ответа", onClick = onOpen)
                .semantics { contentDescription = reference.accessibleSource },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reference.attachments.firstNotNullOfOrNull { it.image }?.let {
                AuroraAttachmentThumbnail(it, Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))) {}
            }
            BasicText(
                referencePreview(reference, reference.replyPreviewFragment), style = auroraMessageStyle(),
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            AuroraReferenceMark(reference.accent, Modifier.align(Alignment.Top).padding(top = 2.dp, end = 4.dp))
        }
        if (onRemove != null) {
            AuroraReferenceButton("Удалить ответ", onRemove) {
                AuroraReferenceIcon(Res.drawable.chat_reference_close, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun AuroraQuoteReference(
    reference: AuroraMessageReference,
    touch: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    var expanded by remember(reference) { mutableStateOf(false) }
    var canExpand by remember(reference) { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val hover by interactions.collectIsHoveredAsState()
    val focus by interactions.collectIsFocusedAsState()
    val showFocus = focus && HbFocusIndication.keyboardNavigation
    val surface = when {
        reference.isUser && showFocus -> Color(0xFFD1E5C8)
        reference.isUser && hover -> Color(0xFFDDEED5)
        reference.isUser -> Color(0xFFEAF4E3)
        showFocus -> Color(0xFFDFD1EF)
        hover -> Color(0xFFE9DFF3)
        else -> Color(0xFFF1EBF8)
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (selected) LocalTextSelectionColors.current.backgroundColor.compositeOver(surface) else surface)
            .semantics { if (selected) this.selected = true }
            .referenceAccent(reference.accent).padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).clickable(
                interactionSource = interactions, indication = null,
                role = Role.Button, onClickLabel = "К источнику цитаты", onClick = onOpen,
            ).semantics { contentDescription = reference.accessibleSource }.padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (expanded) {
                CompositionLocalProvider(LocalAuroraReferenceRegion provides null) { AuroraExpandedQuote(reference, touch) }
            } else {
                val attachment = reference.attachments.singleOrNull()?.takeIf { reference.fragments.isEmpty() }
                val preview = referencePreview(reference)
                val first = reference.fragments.firstOrNull()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (attachment != null) AuroraReferenceIcon(Res.drawable.chat_reference_file, Modifier.size(16.dp))
                    val text = if (attachment == null) preview else AnnotatedString(listOfNotNull(attachment.name, attachment.sizeLabel)
                        .joinToString(" · "))
                    BasicText(
                        text,
                        style = if (first?.format == Format.Code || first?.format == Format.Diff) {
                            HbTypography.Code.copy(color = Colors.DesktopInk)
                        } else auroraMessageStyle(),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        onTextLayout = { canExpand = it.hasVisualOverflow || reference.attachments.isNotEmpty() },
                    )
                }
            }
        }
        val markLabel = when {
            expanded -> "Свернуть цитату"
            canExpand -> "Развернуть цитату"
            else -> "К источнику цитаты"
        }
        AuroraReferenceButton(
            markLabel,
            onClick = { if (expanded || canExpand) expanded = !expanded else onOpen() },
            modifier = Modifier.align(Alignment.Top),
        ) {
            AuroraReferenceMark(reference.accent)
        }
    }
}

@Composable
private fun AuroraExpandedQuote(reference: AuroraMessageReference, touch: Boolean) {
    val selection = rememberSelectionState()
    reference.fragments.forEach { fragment ->
        val markdown = fragment.markdown
        if (markdown != null) {
            AuroraMarkdownContent(fragment.text, markdown, selection, auroraMessageStyle(), touch)
        } else {
            BasicText(
                fragment.text.referencePreviewText(),
                style = when (fragment.format) {
                    Format.Code, Format.Diff -> HbTypography.Code.copy(color = Colors.DesktopInk)
                    else -> auroraMessageStyle()
                },
            )
        }
    }
    reference.attachments.forEach {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuroraAttachmentThumbnail(it.image, Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))) {
                HbIcon(HbIconGlyph.Document, tint = reference.accent, size = 16.dp)
            }
            BasicText(it.name, style = auroraMessageStyle(), modifier = Modifier.heightIn(min = 16.dp))
        }
    }
}

@Composable
@Suppress("InjectDispatcher") // Preview preparation follows the card's composition.
private fun referencePreview(reference: AuroraMessageReference, previewFragment: Fragment? = null): AnnotatedString {
    val raw = remember(reference, previewFragment) {
        AnnotatedString((previewFragment?.text ?: reference.preview).referencePreviewText())
    }
    val monospace = auroraCodeFontFamily()
    val preview by key(reference, previewFragment, monospace) {
        produceState(raw) {
            val fragments = previewFragment?.let(::listOf) ?: reference.fragments
            if (fragments.none { it.markdown != null }) return@produceState
            value = withContext(Dispatchers.Default) {
                val parts = fragments.map { fragment ->
                    val parsed = fragment.markdown?.document()
                    when {
                        parsed == null -> AnnotatedString(fragment.text.referencePreviewText())
                        parsed.plainText != null -> AnnotatedString(parsed.plainText.orEmpty().referencePreviewText())
                        else -> {
                            val document = parsed.prepareRichDocument(monospace)
                            buildAnnotatedString { appendPreviewBlocks(document.blocks) }
                        }
                    }
                }
                buildAnnotatedString { parts.forEach { append(it) } }
            }
        }
    }
    return preview
}

private fun AnnotatedString.Builder.appendPreviewBlocks(blocks: List<PreparedBlock>) {
    blocks.forEachIndexed { index, block ->
        if (index > 0) append('\n')
        when {
            block.text.isNotEmpty() -> append(AnnotatedString(block.text.text, block.text.spanStyles))
            block.children.isNotEmpty() -> block.children.forEachIndexed { childIndex, child ->
                if (childIndex > 0) append('\n')
                appendPreviewBlocks(child)
            }
            block.table != null -> append(block.table.clipboardText())
            else -> when (val source = block.source) {
                is ReplyBlock.Code -> append(source.source)
                is ReplyBlock.Diagram -> append(source.source)
                else -> Unit
            }
        }
    }
}

internal fun String.referencePreviewText(): String {
    val links = LocalPromptLink.findIn(this).iterator()
    if (!links.hasNext()) return this
    return buildString {
        var offset = 0
        while (links.hasNext()) {
            val link = links.next()
            append(this@referencePreviewText, offset, link.range.first)
            append(link.label)
            offset = link.range.last + 1
        }
        append(this@referencePreviewText, offset, this@referencePreviewText.length)
    }
}

@Composable
internal fun AuroraReferenceIcon(icon: DrawableResource, modifier: Modifier = Modifier) {
    Image(painterResource(icon), contentDescription = null, modifier = modifier)
}

@Composable
private fun AuroraReferenceMark(color: Color, modifier: Modifier = Modifier) {
    Image(painterResource(Res.drawable.chat_reference_quote), contentDescription = null,
        modifier = modifier.size(14.dp), colorFilter = ColorFilter.tint(color))
}

@Composable
private fun AuroraReferenceButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    HbTooltip(label, modifier) {
        Box(
            Modifier.size(28.dp).clickable(indication = null, interactionSource = null, role = Role.Button,
                onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

private val AuroraMessageReference.accent: Color
    get() = if (isUser) Color(0xFF2F6B43) else Colors.Primary

private val AuroraMessageReference.accessibleSource: String
    get() = if (isUser) "Источник пользователя" else "Источник агента"

private fun Modifier.referenceAccent(color: Color) = drawBehind {
    drawRect(color, size = Size(2.dp.toPx(), size.height))
}
