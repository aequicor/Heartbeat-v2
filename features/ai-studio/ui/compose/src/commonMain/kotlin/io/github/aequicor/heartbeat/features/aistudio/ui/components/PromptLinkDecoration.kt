package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as Colors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

@Composable
internal fun promptLinkSpanStyle() =
    HbTypography.Metadata.copy(color = Colors.Primary, fontWeight = FontWeight.Medium).toSpanStyle()

internal fun Modifier.promptLinkDecoration(
    layoutProvider: () -> TextLayoutResult?,
    links: List<TextRange>,
    icon: Painter,
    scrollProvider: (TextLayoutResult) -> Float = { 0f },
): Modifier = drawWithContent {
    val layout = layoutProvider()
    if (layout == null) {
        drawContent()
        return@drawWithContent
    }
    val scroll = scrollProvider(layout)
    val spanHeight = Layout.PromptLinkHeight.toPx()
    val radius = CornerRadius(Layout.ControlGap.toPx())
    links.forEach { span ->
        val first = layout.getBoundingBox(span.start)
        val last = layout.getBoundingBox(span.end - 1)
        val firstLine = layout.getLineForOffset(span.start)
        val lastLine = layout.getLineForOffset(span.end - 1)
        for (line in firstLine..lastLine) {
            val start = if (line == firstLine) first.left else layout.getLineLeft(line)
            val end = if (line == lastLine) last.right else layout.getLineRight(line)
            val center = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f - scroll
            val top = center - spanHeight / 2f
            if (end > start && top < size.height && top + spanHeight > 0f) {
                drawRoundRect(Colors.PromptReferenceChip, Offset(start, top), Size(end - start, spanHeight), radius)
            }
        }
    }
    drawContent()
    val edge = Layout.PromptLinkIcon.toPx()
    links.forEach { span ->
        val first = layout.getBoundingBox(span.start)
        val line = layout.getLineForOffset(span.start)
        val center = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f - scroll
        if (center in 0f..size.height) {
            translate(first.left + Layout.ControlGap.toPx(), center - edge / 2f) {
                with(icon) { draw(Size(edge, edge)) }
            }
        }
    }
}
