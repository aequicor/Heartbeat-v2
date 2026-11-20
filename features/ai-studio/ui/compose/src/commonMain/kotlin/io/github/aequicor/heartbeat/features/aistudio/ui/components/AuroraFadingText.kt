package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors

@Composable
internal fun AuroraFadingText(
    text: String,
    style: TextStyle,
    fadeWidth: Dp,
    modifier: Modifier = Modifier,
    onOverflowChange: (Boolean) -> Unit = {},
) {
    var truncated by remember(text) { mutableStateOf(false) }
    val fade = if (!truncated) Modifier else Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val mask = Brush.horizontalGradient(
                listOf(AuroraColors.ContentMask, Color.Transparent),
                startX = (size.width - fadeWidth.toPx()).coerceAtLeast(0f),
                endX = size.width,
            )
            onDrawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        }
    BasicText(
        text, modifier.then(fade), style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            val overflow = layout.getLineRight(0) > layout.size.width
            if (overflow != truncated) {
                truncated = overflow
                onOverflowChange(overflow)
            }
        },
    )
}
