package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbAvatar
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Dense navigation rail of the studio: destinations, the settings action and the identity of the
 * current person. The avatar follows the in-memory visibility preference.
 */
@Composable
internal fun AiStudioNavigationRail(
    avatarVisible: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(HbPaneGeometry.NavigationRail.dp)
            .background(colors.surface.subtle)
            .padding(horizontal = HbSpacing.Xxs, vertical = HbSpacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        Spacer(Modifier.height(HbSizes.paneHeaderHeight))
        AiStudioRailItem()
        Spacer(Modifier.weight(1f))
        HbIconButton(
            contentDescription = "Настройки",
            onClick = onOpenSettings,
        ) {
            AiStudioIcon(
                glyph = AiStudioGlyph.Settings,
                modifier = Modifier.size(HbSizes.iconSize),
                tint = colors.text.secondary,
            )
        }
        if (avatarVisible) {
            HbAvatar(
                initials = "К",
                contentDescription = "Профиль",
            )
        }
        Spacer(Modifier.height(HbSpacing.Xs))
    }
}

@Composable
private fun AiStudioRailItem(
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HbShapes.Medium)
            .background(colors.selection.background)
            .padding(vertical = HbSpacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        AiStudioIcon(
            glyph = AiStudioGlyph.Chat,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = colors.selection.foreground,
        )
        BasicText(
            text = "Чат",
            style = HbTypography.Metadata,
            color = { colors.selection.foreground },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
