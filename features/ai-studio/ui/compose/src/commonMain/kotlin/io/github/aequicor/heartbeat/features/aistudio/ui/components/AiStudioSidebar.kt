package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.components.HbSearchField
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Projects, recent conversations and appearance controls of the studio. */
@Composable
internal fun AiStudioSidebar(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.surface.base)
            .padding(horizontal = HbSpacing.M),
    ) {
        AiStudioSidebarBrand()
        Spacer(Modifier.height(HbSpacing.M))
        HbSearchField(
            value = query,
            onValueChange = { query = it },
            clearDescription = "Очистить поиск",
            placeholder = "Поиск чатов и проектов…",
            modifier = Modifier.semantics {
                contentDescription = "Поиск чатов и проектов"
            },
        )
        Spacer(Modifier.height(HbSpacing.M))
        HbButton(
            text = "+  Новый чат",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(HbSpacing.L))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            AiStudioProjectList()
            Spacer(Modifier.height(HbSpacing.L))
            AiStudioRecentChatList()
        }

        HbDivider()
        AiStudioAppearanceRow()
        AiStudioSyncRow()
        Spacer(Modifier.height(HbSpacing.M))
    }
}

@Composable
private fun AiStudioSidebarBrand(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.paneHeaderHeight)
            .padding(vertical = HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AiStudioBrandMark(
            modifier = Modifier
                .width(HbSpacing.Xl)
                .height(HbSpacing.L),
        )
        BasicText(
            text = "Heartbeat Studio",
            style = HbTypography.Title,
            color = { colors.text.primary },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiStudioProjectList(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HbSizes.navigationRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "Проекты",
                style = HbTypography.Caption,
                color = { colors.text.secondary },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = HbSpacing.S),
            )
            HbIconButton(
                contentDescription = "Новый проект",
                onClick = {},
            ) {
                HbIcon(
                    glyph = HbIconGlyph.Plus,
                    size = HbSizes.iconSize,
                    tint = colors.text.secondary,
                )
            }
        }
        projects.forEach { project ->
            AiStudioProjectRow(project = project)
        }
    }
}

@Composable
private fun AiStudioProjectRow(
    project: AiStudioProject,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    val contentColor = if (project.selected) {
        colors.selection.foreground
    } else {
        colors.text.primary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.navigationRowHeight)
            .clip(HbShapes.Small)
            .background(if (project.selected) colors.selection.background else Color.Transparent)
            .clickable(role = Role.Button, onClick = {})
            .padding(
                start = HbSpacing.S,
                end = HbSpacing.Xs,
                top = HbSpacing.Xxs,
                bottom = HbSpacing.Xxs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AiStudioIcon(
            glyph = AiStudioGlyph.Folder,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = if (project.selected) colors.selection.indicator else colors.text.secondary,
        )
        BasicText(
            text = project.name,
            style = HbTypography.Label,
            color = { contentColor },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (project.selected) {
            HbIconButton(
                contentDescription = "Действия с проектом",
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
}

@Composable
private fun AiStudioRecentChatList(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        BasicText(
            text = "Недавние чаты",
            style = HbTypography.Caption,
            color = { colors.text.secondary },
            modifier = Modifier.padding(
                start = HbSpacing.S,
                top = HbSpacing.Xs,
                bottom = HbSpacing.Xs,
            ),
        )
        recentChats.forEach { chat ->
            AiStudioRecentChatRow(chat = chat)
        }
    }
}

@Composable
private fun AiStudioRecentChatRow(
    chat: AiStudioChat,
    modifier: Modifier = Modifier,
) {
    val colors = HbTheme.colors
    val contentColor = if (chat.selected) colors.selection.foreground else colors.text.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.navigationRowHeight)
            .clip(HbShapes.Small)
            .background(if (chat.selected) colors.selection.background else Color.Transparent)
            .clickable(role = Role.Button, onClick = {})
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AiStudioIcon(
            glyph = AiStudioGlyph.Chat,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = if (chat.selected) colors.selection.indicator else colors.text.secondary,
        )
        BasicText(
            text = chat.title,
            style = HbTypography.Label,
            color = { contentColor },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = chat.time,
            style = HbTypography.Metadata,
            color = { colors.text.secondary },
            maxLines = 1,
        )
    }
}

@Composable
private fun AiStudioAppearanceRow(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.settingsRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AiStudioIcon(
            glyph = AiStudioGlyph.Sun,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = colors.text.secondary,
        )
        BasicText(
            text = "Светлая тема",
            style = HbTypography.Label,
            color = { colors.text.primary },
            modifier = Modifier.weight(1f),
        )
        HbSwitch(
            label = "",
            checked = true,
            onCheckedChange = {},
        )
    }
}

@Composable
private fun AiStudioSyncRow(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.settingsRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        AiStudioIcon(
            glyph = AiStudioGlyph.Monitor,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = colors.text.secondary,
        )
        BasicText(
            text = "Синхронизация",
            style = HbTypography.Label,
            color = { colors.text.primary },
            modifier = Modifier.weight(1f),
        )
        AiStudioIcon(
            glyph = AiStudioGlyph.Cloud,
            modifier = Modifier.size(HbSizes.iconSize),
            tint = colors.status.success.foreground,
        )
        BasicText(
            text = "Включена",
            style = HbTypography.Metadata,
            color = { colors.status.success.foreground },
        )
    }
}

private data class AiStudioProject(
    val name: String,
    val selected: Boolean = false,
)

private data class AiStudioChat(
    val title: String,
    val time: String,
    val selected: Boolean = false,
)

private val projects = listOf(
    AiStudioProject(name = "Heartbeat Studio", selected = true),
    AiStudioProject(name = "Продукт и стратегия"),
    AiStudioProject(name = "Дизайн и UX"),
    AiStudioProject(name = "Идеи и исследования"),
    AiStudioProject(name = "Личные заметки"),
)

private val recentChats = listOf(
    AiStudioChat(title = "Концепция AI-студии", time = "10:24", selected = true),
    AiStudioChat(title = "Сравнение моделей", time = "Вчера"),
    AiStudioChat(title = "Архитектура агентов", time = "Вчера"),
    AiStudioChat(title = "Идеи для плагинов", time = "12 янв"),
    AiStudioChat(title = "Структура промптов", time = "11 янв"),
)
