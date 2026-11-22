package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as Aurora
import io.github.aequicor.heartbeat.designsystem.theme.HbEngineSettingsLayout as Layout
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SettingsText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HbTypography.Label,
    color: Color = HbTheme.colors.text.primary,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun SettingsRule(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(Aurora.Stroke).background(HbTheme.colors.border.subtle))
}

@Composable
internal fun SettingsIcon(resource: DrawableResource, color: Color, size: Dp = Aurora.Glyph) {
    Image(painterResource(resource), null, Modifier.size(size), colorFilter = ColorFilter.tint(color))
}

@Composable
internal fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: HbButtonVariant = HbButtonVariant.Secondary,
    enabled: Boolean = true,
    height: Dp = Layout.fieldHeight,
    startAligned: Boolean = false,
    description: String = "",
    tint: Color? = null,
    horizontalInset: Dp = if (text.isEmpty()) HbSpacing.Xs else Aurora.ContentInset,
    icon: @Composable (Color) -> Unit = {},
) {
    val colors = HbTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val foreground = settingsButtonForeground(enabled, tint, variant)
    val fill = settingsButtonFill(enabled, variant)
    val background = settingsButtonBackground(enabled, pressed, hovered, fill, foreground)
    val border = when {
        focused -> colors.focus
        variant == HbButtonVariant.Secondary && enabled -> colors.border.control
        else -> null
    }
    val shape = RoundedCornerShape(Aurora.SmallRadius)
    Row(
        modifier.heightIn(min = height).clip(shape).background(background)
            .then(if (border != null) Modifier.border(Aurora.Stroke, border, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick)
            .then(if (description.isEmpty()) Modifier else Modifier.semantics { contentDescription = description })
            .padding(horizontal = horizontalInset, vertical = HbSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S, if (startAligned) Alignment.Start
            else Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(foreground)
        if (text.isNotEmpty()) SettingsText(text, color = foreground, maxLines = 1)
    }
}

private const val PressedOverlayAmount = 0.14f
private const val HoveredOverlayAmount = 0.07f

@Composable
@ReadOnlyComposable
private fun settingsButtonBackground(
    enabled: Boolean,
    pressed: Boolean,
    hovered: Boolean,
    fill: Color,
    foreground: Color,
): Color {
    val subtle = HbTheme.colors.surface.subtle
    return when {
        !enabled -> fill
        pressed -> lerp(if (fill == Color.Transparent) subtle else fill, foreground, PressedOverlayAmount)
        hovered -> lerp(if (fill == Color.Transparent) subtle else fill, foreground, HoveredOverlayAmount)
        else -> fill
    }
}

@Composable
@ReadOnlyComposable
private fun settingsButtonForeground(enabled: Boolean, tint: Color?, variant: HbButtonVariant): Color {
    val colors = HbTheme.colors
    return when {
        !enabled -> colors.text.muted
        tint != null -> tint
        variant == HbButtonVariant.Primary || variant == HbButtonVariant.Destructive -> colors.action.primaryForeground
        else -> colors.text.primary
    }
}

@Composable
@ReadOnlyComposable
private fun settingsButtonFill(enabled: Boolean, variant: HbButtonVariant): Color {
    val colors = HbTheme.colors
    return when {
        !enabled -> colors.surface.subtle
        variant == HbButtonVariant.Primary -> colors.action.primaryBackground
        variant == HbButtonVariant.Destructive -> colors.status.error.foreground
        variant == HbButtonVariant.Ghost -> Color.Transparent
        else -> colors.surface.base
    }
}

@Composable
internal fun SettingsInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    errorMessage: String? = null,
    singleLine: Boolean = true,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val colors = HbTheme.colors
    val border = when {
        errorMessage != null -> colors.status.error.foreground
        focused -> colors.focus
        else -> colors.border.subtle
    }
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        textStyle = HbTypography.Label.copy(color = colors.text.primary), cursorBrush = SolidColor(colors.text.primary),
        modifier = modifier.onFocusChanged { focused = it.isFocused }.semantics {
            contentDescription = label
            if (errorMessage != null) error(errorMessage)
        },
        decorationBox = { input ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Layout.fieldHeight).clip(HbShapes.Medium)
                    .background(colors.surface.input)
                    .border(Aurora.Stroke, border, HbShapes.Medium).padding(horizontal = Aurora.ContentInset,
                        vertical = HbSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
            ) {
                leading()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) SettingsText(placeholder, color = colors.text.muted, maxLines = 1)
                    input()
                }
                trailing()
            }
        },
    )
}

@Composable
internal fun SettingsModal(
    width: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier.padding(HbSpacing.M), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(Aurora.ChromeInset)
            Column(
                Modifier.width(width).dismissOnEscape(onDismiss).clip(shape).background(HbTheme.colors.surface.raised)
                    .border(Aurora.Stroke, HbTheme.colors.border.subtle, shape),
                content = content,
            )
        }
    }
}
