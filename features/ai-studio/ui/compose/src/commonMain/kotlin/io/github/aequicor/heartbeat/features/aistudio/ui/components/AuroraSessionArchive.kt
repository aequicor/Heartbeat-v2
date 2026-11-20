package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.aurora_archive_chat_icon
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AuroraSessionActions(row: NavigationRow, archiveVisible: Boolean, onAction: (AiStudioAction) -> Unit) {
    if (row.kind != NavigationKind.Chat) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (archiveVisible) {
            SidebarAction(
                "Архивировать диалог «${row.title}»", { onAction(AiStudioAction.ArchiveChat(row.id)) },
                Modifier.width(AuroraLayout.DesktopTarget),
            ) {
                Image(
                    painterResource(Res.drawable.aurora_archive_chat_icon), null, Modifier.size(AuroraLayout.Glyph),
                    colorFilter = ColorFilter.tint(AuroraColors.NavigationIcon),
                )
            }
        }
        val status = row.status
        if (status != null) SidebarStatus(status, Modifier.width(AuroraLayout.DesktopTarget))
    }
}

@Composable
internal fun AuroraSessionMenu(chat: Chat, touch: Boolean, onAction: (AiStudioAction) -> Unit) {
    val target = if (touch) AuroraLayout.TouchTarget else AuroraLayout.DesktopTarget
    val actions = listOf(HbMenuAction("Архивировать диалог", { onAction(AiStudioAction.ArchiveChat(chat.id)) }))
    AuroraMenu(actions) { modifier, open ->
        AuroraControl(
            open, modifier, label = "Действия диалога «${chat.title}»", touch = touch,
            background = Color.Transparent, horizontalPadding = (target - AuroraLayout.Glyph) / 2,
        ) {
            HbIcon(HbIconGlyph.More, tint = AuroraColors.Muted, size = AuroraLayout.Glyph)
        }
    }
}
