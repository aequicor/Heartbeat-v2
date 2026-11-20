package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

@Composable
internal fun HbAuroraMenuSurface(presentation: HbMenuPresentation, content: @Composable (Dp) -> Unit) {
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    val messageActions = presentation == HbMenuPresentation.AuroraDesktopMessageActions
    val surface = if (messageActions) AuroraColors.MessageActionSurface else AuroraColors.Pearl
    val outline = if (messageActions) AuroraColors.MessageActionOutline else AuroraColors.Line
    BoxWithConstraints {
        val height = minOf(maxHeight, AuroraPopupMaxHeight)
        Box(
            Modifier.compactMenuWidth().shadow(AuroraLayout.Space, shape, clip = false)
                .then(HbAuroraOutline(surface, outline, AuroraLayout.SmallRadius)),
        ) { content(height) }
    }
}

@Composable
internal fun HbAuroraMenuItem(
    action: HbMenuAction,
    presentation: HbMenuPresentation,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(AuroraLayout.SmallRadius)
    val messageActions = presentation == HbMenuPresentation.AuroraDesktopMessageActions
    val error = if (messageActions) AuroraColors.MessageActionError else AuroraColors.Error
    val color = if (action.destructive) error else AuroraColors.Ink
    val hover = if (messageActions) AuroraColors.MessageActionHover else AuroraColors.WorkHover
    val surface = if (hovered || active) hover else Color.Transparent
    val minHeight = if (presentation == HbMenuPresentation.AuroraTouchMessage) AuroraLayout.TouchTarget
    else AuroraLayout.DesktopTarget
    val selected = action.selected
    Row(
        modifier
            .fillMaxWidth().heightIn(min = minHeight).clip(shape).background(surface)
            .hbFocusRing(focused, shape, color = AuroraColors.Primary)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction, indication = null, enabled = action.enabled,
                role = Role.Button, onClick = onClick,
            )
            .then(if (selected == null) Modifier else Modifier.semantics { this.selected = selected })
            .padding(horizontal = AuroraLayout.Space, vertical = AuroraLayout.ControlGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space),
    ) {
        action.glyph?.let { HbIcon(it, tint = color, size = AuroraLayout.Glyph) }
        BasicText(
            action.label,
            modifier = Modifier.weight(1f),
            style = HbTypography.Label.copy(color = color),
        )
        when (selected) {
            true -> HbIcon(HbIconGlyph.Check, tint = AuroraColors.Primary, size = AuroraLayout.Glyph)
            false -> Spacer(Modifier.size(AuroraLayout.Glyph))
            null -> Unit
        }
    }
}

private fun Modifier.compactMenuWidth() = layout { measurable, constraints ->
    val intrinsic = measurable.maxIntrinsicWidth(Constraints.Infinity)
    val step = AuroraLayout.SmallGap.roundToPx().coerceAtLeast(1)
    val rounded = ((intrinsic + step - 1) / step) * step
    val width = constraints.constrainWidth(maxOf(rounded, AuroraPopupMinWidth.roundToPx()))
    val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(width, child.height) { child.placeRelative(0, 0) }
}

private val AuroraPopupMinWidth = 112.dp
private val AuroraPopupMaxHeight = 280.dp
