package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

internal fun Modifier.auroraComposerSurface(keyboardFocus: Boolean): Modifier {
    val shape = RoundedCornerShape(AuroraLayout.DesktopComposerRadius)
    val shell = dropShadow(
        shape,
        Shadow(AuroraLayout.ComposerShadowRadius, AuroraColors.ComposerShadow,
            offset = DpOffset(Dp.Hairline, AuroraLayout.ComposerShadowOffset)),
    ).dropShadow(
        shape,
        Shadow(AuroraLayout.ComposerContactRadius, AuroraColors.ComposerContactShadow,
            offset = DpOffset(Dp.Hairline, AuroraLayout.Stroke)),
    ).background(AuroraColors.ComposerSurface, shape).padding(AuroraLayout.FrameStroke)
    return if (keyboardFocus) {
        shell.border(
            AuroraLayout.FrameStroke, AuroraColors.Primary, RoundedCornerShape(AuroraLayout.DesktopComposerInnerRadius),
        )
    } else {
        shell
    }
}

@Composable
internal fun AuroraDraftAttachments(files: List<File>, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    if (touch) {
        Column(verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
            files.forEach { file -> AuroraDraftAttachment(file, touch, onAction) }
        }
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AuroraLayout.Space)) {
            files.forEach { file -> AuroraDraftAttachment(file, touch, onAction) }
        }
    }
}

@Composable
private fun AuroraDraftAttachment(file: File, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    AuroraControl(
        { onAction(AiStudioAction.Detach(file.id)) },
        if (touch) Modifier.padding(horizontal = AuroraLayout.Space) else Modifier,
        label = "Убрать ${file.name}", touch = touch, background = AuroraColors.Canvas, round = false,
    ) {
        if (!touch) {
            HbIcon(HbIconGlyph.Document, tint = AuroraColors.Muted, size = AuroraLayout.Glyph)
        }
        AuroraText(
            file.name,
            (if (touch) Modifier.weight(1f, fill = false) else Modifier).padding(horizontal = AuroraLayout.Space)
                .widthIn(max = AuroraLayout.ParameterMaxWidth),
            small = true, lines = 1,
        )
        HbIcon(HbIconGlyph.Close, tint = AuroraColors.Muted, size = AuroraLayout.SmallGlyph)
    }
}

@Composable
internal fun AuroraAttachMenu(
    choices: List<File>, files: List<File>, touch: Boolean, onAction: (AiStudioAction) -> Unit,
) {
    val actions = choices.map { file ->
        HbMenuAction(
            file.name, { onAction(AiStudioAction.Attach(file.id)) },
            enabled = files.none { it.id == file.id }, glyph = HbIconGlyph.Document,
        )
    }
    if (touch) {
        AuroraMenu(actions) { trigger, open -> AuroraAttachButton(touch, open, trigger) }
    } else {
        AuroraHeaderMenu(actions) { trigger, open -> AuroraAttachButton(touch, open, trigger) }
    }
}

@Composable
internal fun AuroraAttachButton(
    touch: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    demo: Boolean = true,
) {
    AuroraControl(
        onClick, if (touch) modifier else modifier.size(AuroraLayout.DesktopTarget),
        label = if (touch && demo) "Добавить демонстрационное вложение" else "Прикрепить файл",
        touch = touch, enabled = enabled, background = Color.Transparent,
        round = touch, horizontalPadding = AuroraLayout.Space,
    ) {
        HbIcon(
            HbIconGlyph.Plus, tint = if (touch) AuroraColors.Muted else AuroraColors.DesktopInk,
            size = if (touch) AuroraLayout.LargeGlyph else AuroraLayout.Glyph,
        )
    }
}

@Composable
internal fun AuroraSendControl(working: Boolean, enabled: Boolean, touch: Boolean, onClick: () -> Unit) {
    val prominent = working || enabled
    val label = if (working) "Остановить работу" else "Отправить сообщение"
    val face = auroraSendFace(working, enabled, touch)
    val size = if (touch) AuroraLayout.SendCircle else AuroraLayout.DesktopTarget
    val glyphColor = when {
        !touch -> AuroraColors.DesktopOnPrimaryAction
        !prominent -> AuroraColors.Primary
        else -> AuroraColors.Pearl
    }
    AuroraControl(
        onClick, label = label, enabled = enabled, touch = touch,
        background = Color.Transparent,
        focus = if (touch) AuroraColors.Primary else AuroraColors.Pearl,
        horizontalPadding = if (touch) AuroraLayout.Space else Dp.Hairline,
    ) {
        Box(
            Modifier.size(size).background(face, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HbIcon(
                if (working) HbIconGlyph.Stop else HbIconGlyph.ArrowUp,
                tint = glyphColor,
                size = AuroraLayout.Glyph,
            )
        }
    }
}

private fun auroraSendFace(working: Boolean, enabled: Boolean, touch: Boolean): Color = when {
    touch -> if (working || enabled) AuroraColors.Primary else AuroraColors.Lavender
    !enabled -> AuroraColors.ComposerIdleSend
    working -> AuroraColors.Primary
    else -> AuroraColors.DesktopPrimaryAction
}
