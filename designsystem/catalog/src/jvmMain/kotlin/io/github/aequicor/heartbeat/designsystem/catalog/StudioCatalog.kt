@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbAvatar
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbChip
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.components.HbNavigationGroup
import io.github.aequicor.heartbeat.designsystem.components.HbPaneHeader
import io.github.aequicor.heartbeat.designsystem.components.HbStatusRow
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone
import io.github.aequicor.heartbeat.designsystem.components.HbToolbar
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbWorkspaceLayout
import io.github.aequicor.heartbeat.designsystem.patterns.HbApprovalRequest
import io.github.aequicor.heartbeat.designsystem.patterns.HbComposer
import io.github.aequicor.heartbeat.designsystem.patterns.HbComposerState
import io.github.aequicor.heartbeat.designsystem.patterns.HbMessage
import io.github.aequicor.heartbeat.designsystem.patterns.HbMessageRole
import io.github.aequicor.heartbeat.designsystem.patterns.HbSessionRow
import io.github.aequicor.heartbeat.designsystem.patterns.HbSessionState
import io.github.aequicor.heartbeat.designsystem.patterns.HbToolActivity
import io.github.aequicor.heartbeat.designsystem.patterns.HbToolStatus
import io.github.aequicor.heartbeat.designsystem.patterns.HbTranscript
import io.github.aequicor.heartbeat.designsystem.patterns.HbUsageDisplay
import io.github.aequicor.heartbeat.designsystem.patterns.HbWorkspaceScaffold
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Composed studio slice of the catalog (HB-DS-031, HB-DS-032): navigation, session list, transcript,
 * approval, composer, inspector and usage composed with the resolved workspace fit. The slice is a
 * review example, not a product screen; it proves that the patterns compose without a second layout
 * implementation and that the same fit drives every region.
 */
@Composable
internal fun StudioCatalogScreen(modifier: Modifier = Modifier) {
    val fit = HbWorkspaceLayout.fit(availableWidth = StudioWidth)
    var following by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("Проверь журнал сборки и предложи исправление.") }

    HbWorkspaceScaffold(
        fit = fit,
        modifier = modifier.fillMaxSize(),
        navigation = { Navigation() },
        sidebar = { SessionList() },
        secondPane = { BasicText("") },
        inspector = { InspectorPane() },
        content = {
            Conversation(
                following = following,
                onFollowingChange = { following = it },
                draft = draft,
                onDraftChange = { draft = it },
            )
        },
    )
}

@Composable
private fun Navigation(modifier: Modifier = Modifier) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(HbSpacing.Xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        HbAvatar(initials = "HE", contentDescription = "Heartbeat")
        HbIconButton(contentDescription = "Поиск по истории", onClick = {}) {
            HbIcon(glyph = HbIconGlyph.Search, tint = colors.text.secondary)
        }
        HbIconButton(contentDescription = "Новый диалог", onClick = {}) {
            HbIcon(glyph = HbIconGlyph.Plus, tint = colors.text.secondary)
        }
        HbIconButton(contentDescription = "Ещё", onClick = {}) {
            HbIcon(glyph = HbIconGlyph.More, tint = colors.text.secondary)
        }
    }
}

@Composable
private fun SessionList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(HbSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
    ) {
        HbNavigationGroup(title = "Недавние") {
            HbSessionRow(
                title = "Разбор релиза",
                project = "Heartbeat",
                state = HbSessionState.Running,
                selected = true,
                onClick = {},
            )
            HbSessionRow(
                title = "Подготовка конспекта",
                project = "Исследования",
                state = HbSessionState.Unread,
                onClick = {},
            )
            HbSessionRow(
                title = "Обновление подключения",
                project = "Heartbeat",
                state = HbSessionState.Approval,
                onClick = {},
                actions = { HbChip(label = "1") },
            )
        }
    }
}

@Composable
private fun Conversation(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(modifier = modifier.fillMaxSize()) {
        HbPaneHeader(
            title = "Разбор релиза",
            subtitle = "heartbeat-fast · Проект «Heartbeat»",
            actions = { HbChip(label = "Идёт ответ") },
        )
        HbToolbar(
            actions = {
                HbButton(text = "Остановить", onClick = {})
                HbChip(label = "3 вложения")
            },
        )
        HbTranscript(
            following = following,
            onFollowingChange = onFollowingChange,
            jumpToLatestLabel = "К последнему",
            newContentLabel = "Новое сообщение",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            transcriptItems(colors = colors)
        }
        HbComposer(
            value = draft,
            onValueChange = onDraftChange,
            state = HbComposerState.Generating,
            placeholder = "Напишите сообщение…",
            label = "Черновик сообщения",
            sendLabel = "Отправить",
            stopLabel = "Остановить",
            modelLabel = "heartbeat-fast",
            notice = "Ответ формируется…",
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.canvas)
                .padding(HbSpacing.S),
        )
    }
}

private fun LazyListScope.transcriptItems(colors: HbColors) {
    item {
        HbMessage(role = HbMessageRole.User, roleLabel = "Вы") {
            BasicText(
                text = "Проверь журнал сборки и предложи исправление.",
                style = HbTypography.Body,
                color = { colors.text.primary },
            )
        }
    }
    item {
        HbMessage(
            role = HbMessageRole.Assistant,
            roleLabel = "Ассистент",
            meta = "12:45",
        ) {
            BasicText(
                text = "Сборка падает на задаче проверки зависимостей: модуль ссылается на Compose.",
                style = HbTypography.Body,
                color = { colors.text.primary },
            )
        }
    }
    item {
        HbToolActivity(
            title = "read_file",
            target = "settings.gradle.kts",
            status = HbToolStatus.Succeeded,
            expanded = false,
            onExpandedChange = {},
        ) {}
    }
    item {
        HbApprovalRequest(
            action = "Записать файл",
            target = "settings.gradle.kts",
            context = "Удалить строку с Compose",
            scope = "Только этот файл",
            decisions = {
                HbButton(text = "Разрешить один раз", onClick = {})
                HbButton(text = "Отклонить", onClick = {})
            },
        )
    }
}

@Composable
private fun InspectorPane(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(HbSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        HbStatusRow(label = "Инструменты", detail = "1 выполнен", tone = HbStatusTone.Success)
        HbUsageDisplay(label = "Контекст", value = "42%", detail = "Обновлено сейчас")
        HbUsageDisplay(label = "Квота провайдера", value = "81%", detail = "Данные устарели")
        Row(modifier = Modifier.width(InspectorRowWidth)) {
            HbStatusRow(label = "Источники", tone = HbStatusTone.Info, detail = "3 файла")
        }
    }
}

private const val StudioWidth = 1440

private val InspectorRowWidth = 200.dp
