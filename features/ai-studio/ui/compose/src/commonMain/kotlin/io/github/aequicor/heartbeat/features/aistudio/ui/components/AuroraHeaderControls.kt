package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuEntry
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

internal fun Modifier.auroraHeaderSurface(radius: Dp, outline: Boolean = true): Modifier {
    val shape = RoundedCornerShape(radius)
    return shadow(AuroraLayout.ControlGap, shape, clip = false)
        .background(AuroraColors.Pearl, shape)
        .then(if (outline) Modifier.border(AuroraLayout.Stroke, AuroraColors.HeaderOutline, shape) else Modifier)
}

internal fun Modifier.auroraFloatingHeaderSurface(fill: Color, outline: Color? = null): Modifier {
    val shape = RoundedCornerShape(AuroraLayout.FloatingHeaderRadius)
    return dropShadow(
        shape,
        Shadow(
            AuroraLayout.FloatingHeaderShadowRadius, AuroraColors.HeaderShadow,
            offset = DpOffset(Dp.Hairline, AuroraLayout.FloatingHeaderShadowOffset),
        ),
    ).dropShadow(
        shape,
        Shadow(
            AuroraLayout.FloatingHeaderContactRadius, AuroraColors.HeaderContactShadow,
            offset = DpOffset(Dp.Hairline, AuroraLayout.Stroke),
        ),
    ).background(fill, shape)
        .then(if (outline == null) Modifier else Modifier.border(AuroraLayout.Stroke, outline, shape))
}

@Composable
internal fun AuroraHeaderButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = AuroraLayout.DesktopTarget,
    radius: Dp = AuroraLayout.SmallRadius,
    contentPadding: PaddingValues = PaddingValues(Dp.Hairline),
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val showFocus = focused && HbFocusIndication.keyboardNavigation
    val shape = RoundedCornerShape(radius)
    Row(
        modifier.heightIn(min = height).widthIn(min = AuroraLayout.DesktopTarget)
            .clip(shape)
            .then(if (enabled && hovered) Modifier.background(AuroraColors.NavigationHover) else Modifier)
            .then(if (showFocus) Modifier.border(AuroraLayout.Stroke, AuroraColors.Primary, shape) else Modifier)
            .semantics { contentDescription = label }
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding)
            .padding(vertical = AuroraLayout.HeaderControlVerticalInset),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun AuroraHeaderMenu(
    actions: List<HbMenuEntry>,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    touch: Boolean = false,
    trigger: @Composable (Modifier, () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    HbMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        actions = actions,
        modifier = modifier,
        presentation = if (touch) HbMenuPresentation.AuroraTouchMessage else HbMenuPresentation.AuroraMessage,
        alignEnd = alignEnd,
    ) { triggerModifier -> trigger(triggerModifier) { expanded = true } }
}

@Composable
internal fun AuroraHeaderText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AuroraColors.Ink,
    style: TextStyle = HbTypography.Label,
) {
    BasicText(
        text, modifier,
        style.copy(color = color),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}
