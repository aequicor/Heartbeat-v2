@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.patterns.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbChip
import io.github.aequicor.heartbeat.designsystem.components.HbLink
import io.github.aequicor.heartbeat.designsystem.patterns.HbApprovalRequest
import io.github.aequicor.heartbeat.designsystem.patterns.HbApprovalState
import io.github.aequicor.heartbeat.designsystem.patterns.HbMessage
import io.github.aequicor.heartbeat.designsystem.patterns.HbMessageRole
import io.github.aequicor.heartbeat.designsystem.patterns.HbSessionRow
import io.github.aequicor.heartbeat.designsystem.patterns.HbSessionState
import io.github.aequicor.heartbeat.designsystem.patterns.HbToolActivity
import io.github.aequicor.heartbeat.designsystem.patterns.HbToolStatus
import io.github.aequicor.heartbeat.designsystem.patterns.HbUsageDisplay
import io.github.aequicor.heartbeat.designsystem.patterns.HbUsageState
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Rendered examples of the product patterns (HB-DS-039). */
@Composable
internal fun PatternsCatalogScreen(modifier: Modifier = Modifier) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.canvas)
            .padding(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.M),
    ) {
        SessionsSection()
        MessagesSection()
        ToolsSection()
        ApprovalsSection()
        UsageSection()
    }
}

@Composable
private fun SessionsSection(modifier: Modifier = Modifier) {
    Section(title = "Сессии", modifier = modifier) {
        Column(modifier = Modifier.width(SECTION_WIDTH)) {
            HbSessionRow(
                title = "Разбор релиза",
                project = "Heartbeat",
                state = HbSessionState.Running,
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
                actions = { HbChip(label = "3") },
            )
            HbSessionRow(
                title = "Выбранный диалог",
                project = "Heartbeat",
                selected = true,
                onClick = {},
            )
        }
    }
}

@Composable
private fun MessagesSection(modifier: Modifier = Modifier) {
    val colors = HbThemeDefaults.LocalColors.current

    Section(title = "Сообщения", modifier = modifier) {
        Column(modifier = Modifier.width(SECTION_WIDTH)) {
            HbMessage(role = HbMessageRole.User, roleLabel = "Вы") {
                BasicText(
                    text = "Проверь журнал сборки и предложи исправление.",
                    style = HbTypography.Body,
                    color = { colors.text.primary },
                )
            }
            HbMessage(
                role = HbMessageRole.Assistant,
                roleLabel = "Ассистент",
                meta = "12:45",
                actions = {
                    HbLink(text = "Копировать", onClick = {})
                },
            ) {
                BasicText(
                    text = "Сборка падает на задаче проверки зависимостей.",
                    style = HbTypography.Body,
                    color = { colors.text.primary },
                )
            }
        }
    }
}

@Composable
private fun ToolsSection(modifier: Modifier = Modifier) {
    val colors = HbThemeDefaults.LocalColors.current

    Section(title = "Инструменты", modifier = modifier) {
        Column(modifier = Modifier.width(SECTION_WIDTH)) {
            HbToolActivity(
                title = "read_file",
                target = "docs/specs/design-system.md",
                status = HbToolStatus.Succeeded,
                expanded = true,
                onExpandedChange = {},
                stateDescription = "Развёрнуто",
                details = {
                    BasicText(
                        text = "442 строки",
                        style = HbTypography.Caption,
                        color = { colors.text.secondary },
                    )
                },
            )
            HbToolActivity(
                title = "run_tests",
                target = "./gradlew test",
                status = HbToolStatus.Running,
                expanded = false,
                onExpandedChange = {},
            ) {}
            HbToolActivity(
                title = "apply_patch",
                target = "settings.gradle.kts",
                status = HbToolStatus.Failed,
                expanded = false,
                onExpandedChange = {},
            ) {}
        }
    }
}

@Composable
private fun ApprovalsSection(modifier: Modifier = Modifier) {
    Section(title = "Подтверждения", modifier = modifier) {
        Column(
            modifier = Modifier.width(SECTION_WIDTH),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            HbApprovalRequest(
                action = "Записать файл",
                target = "designsystem/compose/components/HbButton.kt",
                context = "Изменение стиля основной кнопки",
                scope = "Только этот файл",
                decisions = {
                    HbButton(text = "Разрешить один раз", onClick = {})
                    HbButton(text = "Отклонить", onClick = {})
                },
            )
            HbApprovalRequest(
                action = "Записать файл",
                target = "designsystem/patterns/HbSessionRow.kt",
                state = HbApprovalState.Allowed,
                context = "Разрешено один раз",
            )
        }
    }
}

@Composable
private fun UsageSection(modifier: Modifier = Modifier) {
    Section(title = "Расход", modifier = modifier) {
        Column(modifier = Modifier.width(SECTION_WIDTH)) {
            HbUsageDisplay(label = "Контекст", value = "42%", detail = "Обновлено 2 мин назад")
            HbUsageDisplay(
                label = "Квота провайдера",
                value = "Недоступно",
                state = HbUsageState.Unknown,
            )
            HbUsageDisplay(
                label = "Квота провайдера",
                value = "81%",
                state = HbUsageState.Stale,
                detail = "Данные устарели",
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
        BasicText(
            text = title,
            style = HbTypography.Title,
            color = { colors.text.primary },
        )
        content()
    }
}

private val SECTION_WIDTH = 640.dp
