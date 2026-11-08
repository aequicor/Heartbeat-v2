package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbLayoutBreakpoints
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Composer of the studio. The draft is local UI state for the layout pass; the feature store will
 * own the accepted message, submission and queueing states.
 */
@Composable
internal fun AiStudioComposerBar(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors
    var draft by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < HbLayoutBreakpoints.NarrowPane.dp
        val horizontalPadding = if (compact) HbSpacing.M else HbSpacing.Xl

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = HbSpacing.M),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = HbPaneGeometry.ReadingMax.dp)
                    .fillMaxWidth()
                    .clip(HbShapes.Large)
                    .background(colors.surface.input)
                    .border(AiStudioMetrics.hairline, colors.border.control, HbShapes.Large)
                    .padding(HbSpacing.M),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HbSizes.settingsRowHeight)
                        .semantics {
                            contentDescription = "Сообщение Sonnet 3.7 Craft"
                        },
                    textStyle = HbTypography.Body.copy(color = colors.text.primary),
                    cursorBrush = SolidColor(colors.text.primary),
                    maxLines = ComposerMaxLines,
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isEmpty()) {
                                BasicText(
                                    text = ComposerPlaceholder,
                                    style = HbTypography.Body,
                                    color = { colors.text.secondary },
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Spacer(Modifier.height(HbSpacing.S))
                AiStudioComposerActions(compact = compact)
            }
        }
    }
}

@Composable
private fun AiStudioComposerActions(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
        ) {
            HbIconButton(
                contentDescription = "Добавить вложение",
                onClick = {},
            ) {
                HbIcon(
                    glyph = HbIconGlyph.Plus,
                    size = HbSizes.iconSize,
                    tint = colors.text.secondary,
                )
            }
            if (!compact) {
                AiStudioComposerMenu(
                    label = "Инструменты",
                    glyph = AiStudioGlyph.Sliders,
                )
                HbDivider(
                    vertical = true,
                    modifier = Modifier.height(HbSpacing.M),
                )
                AiStudioComposerMenu(
                    label = "Агенты",
                    glyph = AiStudioGlyph.User,
                )
                AiStudioComposerMenu(
                    label = "Плагины",
                    glyph = AiStudioGlyph.Plugins,
                )
            }
            Spacer(Modifier.weight(1f))
            AiStudioSendButton()
        }
        if (compact) {
            Spacer(Modifier.height(HbSpacing.Xs))
            AiStudioCompactComposerActions()
        }
    }
}

@Composable
private fun AiStudioCompactComposerActions(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        AiStudioComposerIconMenu(
            label = "Инструменты",
            glyph = AiStudioGlyph.Sliders,
        )
        AiStudioComposerIconMenu(
            label = "Агенты",
            glyph = AiStudioGlyph.User,
        )
        AiStudioComposerIconMenu(
            label = "Плагины",
            glyph = AiStudioGlyph.Plugins,
        )
    }
}

@Composable
private fun AiStudioComposerIconMenu(
    label: String,
    glyph: AiStudioGlyph,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    HbIconButton(
        contentDescription = label,
        onClick = {},
        modifier = modifier,
    ) {
        AiStudioIcon(
            glyph = glyph,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = colors.text.secondary,
        )
    }
}

@Composable
private fun AiStudioComposerMenu(
    label: String,
    glyph: AiStudioGlyph,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier
            .heightIn(min = HbSizes.controlHeight)
            .clip(HbShapes.Small)
            .clickable(role = Role.Button, onClick = {})
            .padding(horizontal = HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        AiStudioIcon(
            glyph = glyph,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = colors.text.secondary,
        )
        BasicText(
            text = label,
            style = HbTypography.Caption,
            color = { colors.text.secondary },
            maxLines = 1,
        )
        HbIcon(
            glyph = HbIconGlyph.ChevronDown,
            size = HbSizes.controlGlyph,
            tint = colors.text.secondary,
        )
    }
}

@Composable
private fun AiStudioSendButton(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Box(
        modifier = modifier
            .size(HbSizes.controlHeight + HbSpacing.Xxs)
            .clip(HbShapes.Medium)
            .background(colors.accent.brand)
            .clickable(role = Role.Button, onClick = {})
            .semantics {
                contentDescription = "Отправить сообщение"
            },
        contentAlignment = Alignment.Center,
    ) {
        HbIcon(
            glyph = HbIconGlyph.Send,
            size = HbSizes.iconSize,
            tint = colors.action.primaryForeground,
        )
    }
}

private const val ComposerMaxLines = 4

private const val ComposerPlaceholder = "Сообщение Sonnet 3.7 Craft…"
