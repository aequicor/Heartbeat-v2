@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.components.HbFocusIndication
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuEntry
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

@Composable
@ReadOnlyComposable
internal fun auroraTextStyle(): TextStyle = HbTypography.Label.copy(
    color = AuroraColors.Ink,
)

@Composable
internal fun AuroraText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AuroraColors.Ink,
    small: Boolean = false,
    lines: Int = Int.MAX_VALUE,
) {
    val role = when {
        small -> HbTypography.Caption
        else -> HbTypography.Label
    }
    val style = role.copy(
        color = color,
    )
    if (replyTextRevealProvided()) {
        AuroraReplyText(
            text, modifier, style, revealKey = remember { Any() }, maxLines = lines, overflow = TextOverflow.Ellipsis, cascade = false,
        )
    } else {
        BasicText(text, modifier, style, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AuroraControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    touch: Boolean = false,
    enabled: Boolean = true,
    background: Color = AuroraColors.Pearl,
    focus: Color = AuroraColors.Primary,
    round: Boolean = true,
    horizontalPadding: Dp = AuroraLayout.Space,
    minimumTarget: Dp = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget,
    clipContent: Boolean = true,
    hoverBackground: Color? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(if (round) AuroraLayout.ControlRadius else AuroraLayout.SmallRadius)
    var focused by remember { mutableStateOf(false) }
    val focusVisible = focused && HbFocusIndication.keyboardNavigation
    val interactions = interactionSource ?: if (hoverBackground != null) {
        remember { MutableInteractionSource() }
    } else null
    val hovered = interactions?.collectIsHoveredAsState()?.value == true
    val surfaceColor = if (enabled && hovered) hoverBackground ?: background else background
    val surface = if (clipContent) {
        Modifier.clip(shape).background(surfaceColor)
    } else Modifier.background(surfaceColor, shape)
    Row(
        modifier = modifier
            .sizeIn(minHeight = minimumTarget, minWidth = minimumTarget)
            .then(surface)
            .then(if (focusVisible) Modifier.border(AuroraLayout.FrameStroke, focus, shape) else Modifier)
            .then(if (label == null) Modifier else Modifier.semantics { contentDescription = label })
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactions, indication = null,
                enabled = enabled, role = Role.Button, onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun AuroraMenu(
    actions: List<HbMenuEntry>,
    modifier: Modifier = Modifier,
    trigger: @Composable (Modifier, () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    HbMenu(
        expanded = open,
        onDismissRequest = { open = false },
        actions = actions,
        modifier = modifier,
    ) { triggerModifier ->
        trigger(triggerModifier) { open = true }
    }
}

@Composable
internal fun AuroraChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AuroraColors.Ink,
    background: Color = AuroraColors.Pearl,
    touch: Boolean = false,
    enabled: Boolean = true,
) {
    AuroraControl(onClick, modifier, touch = touch, enabled = enabled, background = background, round = false) {
        Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
            AuroraText(text, color = color)
        }
    }
}
