package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbLayoutBreakpoints
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Header of the conversation pane: identity, model and pane actions. */
@Composable
internal fun AiStudioHeader(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < HbLayoutBreakpoints.NarrowPane.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HbSizes.paneHeaderHeight)
                .padding(horizontal = HbSpacing.M, vertical = HbSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
        ) {
            HbIconButton(
                contentDescription = "Вернуться к списку чатов",
                onClick = {},
            ) {
                HbIcon(
                    glyph = HbIconGlyph.ChevronDown,
                    modifier = Modifier.rotate(BackRotationDegrees),
                    size = HbSizes.iconSize,
                    tint = colors.text.secondary,
                )
            }
            AiStudioHeaderTitle(modifier = Modifier.weight(1f))
            AiStudioModelPill(compact = compact)
            AiStudioHeaderActions(compact = compact)
        }
    }
}

@Composable
private fun AiStudioHeaderTitle(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        BasicText(
            text = "Концепция AI-студии",
            style = HbTypography.PaneTitle,
            color = { colors.text.primary },
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HbIcon(
            glyph = HbIconGlyph.ChevronDown,
            size = HbSizes.controlGlyph,
            tint = colors.text.secondary,
        )
    }
}

@Composable
private fun AiStudioHeaderActions(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        if (!compact) {
            HbIconButton(
                contentDescription = "Параметры ответа",
                onClick = {},
            ) {
                AiStudioIcon(
                    glyph = AiStudioGlyph.Sliders,
                    modifier = Modifier.size(HbSizes.iconSize),
                    tint = colors.text.secondary,
                )
            }
            HbIconButton(
                contentDescription = "Боковая панель",
                onClick = {},
            ) {
                AiStudioIcon(
                    glyph = AiStudioGlyph.Panel,
                    modifier = Modifier.size(HbSizes.iconSize),
                    tint = colors.text.secondary,
                )
            }
        }
        HbIconButton(
            contentDescription = "Действия с чатом",
            onClick = {},
        ) {
            HbIcon(
                glyph = HbIconGlyph.More,
                size = HbSizes.iconSize,
                tint = colors.text.secondary,
            )
        }
    }
}

@Composable
private fun AiStudioModelPill(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier
            .heightIn(min = HbSizes.controlHeight)
            .clip(HbShapes.Medium)
            .background(colors.surface.input)
            .border(AiStudioMetrics.hairline, colors.border.control, HbShapes.Medium)
            .padding(
                start = HbSpacing.S,
                end = HbSpacing.Xs,
                top = HbSpacing.Xxs,
                bottom = HbSpacing.Xxs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        AiStudioIcon(
            glyph = AiStudioGlyph.Sparkles,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = colors.accent.brand,
        )
        if (!compact) {
            BasicText(
                text = "Sonnet 3.7 Craft",
                style = HbTypography.Label,
                color = { colors.text.primary },
                maxLines = 1,
            )
        }
        HbIcon(
            glyph = HbIconGlyph.ChevronDown,
            size = HbSizes.controlGlyph,
            tint = colors.text.secondary,
        )
    }
}

private const val BackRotationDegrees = 90f
