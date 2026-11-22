package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_search_chats_icon
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.search_sessions
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SidebarSearchButton(searchVisible: Boolean, onAction: (AiStudioAction) -> Unit) {
    val action = if (searchVisible) AiStudioAction.CollapseChatSearch else AiStudioAction.ExpandChatSearch
    AuroraControl(
        { onAction(action) },
        Modifier.border(
            AuroraLayout.Stroke,
            AuroraColors.NavigationSearchOutline,
            RoundedCornerShape(AuroraLayout.SmallRadius),
        ),
        label = stringResource(Res.string.search_sessions),
        background = AuroraColors.NavigationSearchSurface,
        round = false,
        horizontalPadding = AuroraLayout.ControlGap,
        minimumTarget = AuroraLayout.NavigationRow,
    ) {
        Image(
            painterResource(Res.drawable.aurora_search_chats_icon),
            null,
            Modifier.size(AuroraLayout.Glyph),
            colorFilter = ColorFilter.tint(AuroraColors.NavigationIcon),
        )
    }
}
