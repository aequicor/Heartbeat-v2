package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbAvatar
import io.github.aequicor.heartbeat.designsystem.components.HbBadge
import io.github.aequicor.heartbeat.designsystem.components.HbBanner
import io.github.aequicor.heartbeat.designsystem.components.HbBreadcrumbs
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbChip
import io.github.aequicor.heartbeat.designsystem.components.HbDisclosure
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.components.HbInspector
import io.github.aequicor.heartbeat.designsystem.components.HbKeyHint
import io.github.aequicor.heartbeat.designsystem.components.HbListItem
import io.github.aequicor.heartbeat.designsystem.components.HbNavigationGroup
import io.github.aequicor.heartbeat.designsystem.components.HbNavigationItem
import io.github.aequicor.heartbeat.designsystem.components.HbOrientation
import io.github.aequicor.heartbeat.designsystem.components.HbPaneHeader
import io.github.aequicor.heartbeat.designsystem.components.HbProgress
import io.github.aequicor.heartbeat.designsystem.components.HbSheet
import io.github.aequicor.heartbeat.designsystem.components.HbSnackbar
import io.github.aequicor.heartbeat.designsystem.components.HbSplitter
import io.github.aequicor.heartbeat.designsystem.components.HbSplitterLabels
import io.github.aequicor.heartbeat.designsystem.components.HbStatusRow
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone
import io.github.aequicor.heartbeat.designsystem.components.HbTable
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.components.HbToolbar
import io.github.aequicor.heartbeat.designsystem.components.HbTreeItem
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Navigation, list, structure, metadata, feedback and workspace samples of the sandbox. */
@Composable
internal fun NavigationAndLists(colors: HbColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.M)) {
        Column(modifier = Modifier.width(NAV_WIDTH)) {
            HbNavigationItem(label = "Выбранный раздел", selected = true, onClick = {})
            HbNavigationItem(label = "Обычный раздел", selected = false, onClick = {})
        }
        Column(modifier = Modifier.width(NAV_WIDTH)) {
            HbListItem(
                onClick = {},
                trailing = {
                    HbButton(text = "Открыть", onClick = {})
                },
            ) {
                HbText(text = "Диалог с вложением")
                HbBadge(label = "3", tone = HbStatusTone.Info)
            }
            HbDisclosure(
                title = "Активность инструмента",
                expanded = true,
                onExpandedChange = {},
                stateDescription = "Развёрнуто",
            ) {
                BasicText(
                    text = "read_file · 12 строк",
                    style = HbTypography.Caption,
                    color = { colors.text.secondary },
                )
            }
        }
    }
}

/** Breadcrumbs, a navigation group, a tree and a table. */
@Composable
internal fun Structure() {
    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
        HbBreadcrumbs(
            items = listOf("Проект", "Диалоги", "Разбор релиза"),
            onNavigate = {},
        )
        Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.M)) {
            Column(modifier = Modifier.width(STRUCTURE_NAV_WIDTH)) {
                HbNavigationGroup(title = "Недавние") {
                    HbNavigationItem(label = "Разбор релиза", selected = true, onClick = {})
                    HbNavigationItem(label = "Конспект", selected = false, onClick = {})
                }
            }
            Column(modifier = Modifier.width(STRUCTURE_TREE_WIDTH)) {
                HbTreeItem(
                    label = "designsystem",
                    depth = 0,
                    expanded = true,
                    onExpandedChange = {},
                    stateDescription = "Развёрнуто",
                )
                HbTreeItem(label = "foundation", depth = 1, expanded = false, onExpandedChange = {})
                HbTreeItem(label = "compose", depth = 1, expanded = true, onExpandedChange = {})
                HbTreeItem(label = "components", depth = 2, expanded = false, onExpandedChange = {})
            }
            Column(modifier = Modifier.width(STRUCTURE_TABLE_WIDTH)) {
                HbTable(
                    columns = listOf("Файл", "Изменён", "Статус"),
                    rows = listOf(
                        listOf("HbButton.kt", "10:24", "Изменён"),
                        listOf("HbTable.kt", "10:31", "Добавлен"),
                    ),
                )
            }
        }
    }
}

/** Avatar, chips, badges and a status row. */
@Composable
internal fun Metadata(colors: HbColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HbAvatar(initials = "HB", contentDescription = "Рабочая область")
        HbChip(label = "Выбрано", selected = true)
        HbChip(label = "Тег")
        HbBadge(label = "Готово", tone = HbStatusTone.Success)
        HbBadge(label = "Внимание", tone = HbStatusTone.Warning)
        HbBadge(label = "Ошибка", tone = HbStatusTone.Error)
        HbStatusRow(label = "Подключено", detail = "Проверено 2 мин назад", tone = HbStatusTone.Success)
        BasicText(
            text = "Обычный текст",
            style = HbTypography.Label,
            color = { colors.text.secondary },
        )
    }
}

/** Banner, progress and a pending status row. */
@Composable
internal fun Feedback(colors: HbColors) {
    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
        HbBanner(
            title = "Нет подключения к провайдеру",
            message = "Черновик сохранён. Проверьте адрес или переподключитесь.",
            tone = HbStatusTone.Warning,
            actions = { HbButton(text = "Переподключиться", onClick = {}) },
        )
        HbProgress(progress = 0.42f, label = "Загрузка истории")
        HbProgress(progress = null, label = "Ожидание ответа")
        HbStatusRow(label = "Ожидает подтверждения", tone = HbStatusTone.Warning)
        BasicText(
            text = "Конец каталога",
            style = HbTypography.Metadata,
            color = { colors.text.secondary },
        )
    }
}

/** Pane header, toolbar, splitter and snackbar. */
@Composable
internal fun Workspace(colors: HbColors) {
    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
        HbPaneHeader(
            title = "Диалог",
            subtitle = "Проект «Heartbeat»",
            actions = { HbButton(text = "Остановить", onClick = {}) },
        )
        HbToolbar(
            actions = {
                HbIconButton(contentDescription = "Новый диалог", onClick = {}) {
                    val iconColors = HbThemeDefaults.LocalColors.current
                    BasicText(
                        text = "+",
                        style = HbTypography.Label,
                        color = { iconColors.text.primary },
                    )
                }
                HbKeyHint(keys = listOf("Ctrl", "N"))
            },
        )
        Row(modifier = Modifier.height(96.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colors.surface.subtle),
            )
            HbSplitter(
                orientation = HbOrientation.Vertical,
                onResize = {},
                onReset = null,
                labels = HbSplitterLabels(narrower = "Уже", wider = "Шире", reset = "Сбросить"),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colors.surface.subtle),
            )
        }
        HbSnackbar(
            message = "Черновик сохранён",
            actionLabel = "Отменить",
        )
    }
}

/** Sheet and inspector containers. */
@Composable
internal fun Surfaces(colors: HbColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.M),
        verticalAlignment = Alignment.Top,
    ) {
        HbSheet(
            title = "Параметры ответа",
            onClose = {},
            closeLabel = "Закрыть параметры",
            modifier = Modifier.width(SURFACE_WIDTH),
        ) {
            BasicText(
                text = "Модель: heartbeat-fast",
                style = HbTypography.Caption,
                color = { colors.text.secondary },
            )
        }
        HbInspector(
            title = "Источники",
            onClose = {},
            closeLabel = "Закрыть источники",
            modifier = Modifier.width(SURFACE_WIDTH).height(SURFACE_HEIGHT),
        ) {
            HbStatusRow(label = "3 источника", tone = HbStatusTone.Info)
        }
    }
}

private val NAV_WIDTH = 360.dp

private val SURFACE_WIDTH = 320.dp

private val SURFACE_HEIGHT = 180.dp

private val STRUCTURE_NAV_WIDTH = 220.dp

private val STRUCTURE_TREE_WIDTH = 380.dp

private val STRUCTURE_TABLE_WIDTH = 520.dp
