@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbBadge
import io.github.aequicor.heartbeat.designsystem.components.HbBreadcrumbs
import io.github.aequicor.heartbeat.designsystem.components.HbNavigationGroup
import io.github.aequicor.heartbeat.designsystem.components.HbTable
import io.github.aequicor.heartbeat.designsystem.components.HbTreeItem
import io.github.aequicor.heartbeat.designsystem.components.HbAvatar
import io.github.aequicor.heartbeat.designsystem.components.HbCombobox
import io.github.aequicor.heartbeat.designsystem.components.HbOrientation
import io.github.aequicor.heartbeat.designsystem.components.HbSelect
import io.github.aequicor.heartbeat.designsystem.components.HbSplitter
import io.github.aequicor.heartbeat.designsystem.components.HbSplitterLabels
import io.github.aequicor.heartbeat.designsystem.components.HbBanner
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbCheckbox
import io.github.aequicor.heartbeat.designsystem.components.HbChip
import io.github.aequicor.heartbeat.designsystem.components.HbCopyButton
import io.github.aequicor.heartbeat.designsystem.components.HbDisclosure
import io.github.aequicor.heartbeat.designsystem.components.HbFormField
import io.github.aequicor.heartbeat.designsystem.components.HbIconButton
import io.github.aequicor.heartbeat.designsystem.components.HbInspector
import io.github.aequicor.heartbeat.designsystem.components.HbPaneHeader
import io.github.aequicor.heartbeat.designsystem.components.HbSheet
import io.github.aequicor.heartbeat.designsystem.components.HbSnackbar
import io.github.aequicor.heartbeat.designsystem.components.HbToolbar
import io.github.aequicor.heartbeat.designsystem.components.HbKeyHint
import io.github.aequicor.heartbeat.designsystem.components.HbLink
import io.github.aequicor.heartbeat.designsystem.components.HbListItem
import io.github.aequicor.heartbeat.designsystem.components.HbNavigationItem
import io.github.aequicor.heartbeat.designsystem.components.HbProgress
import io.github.aequicor.heartbeat.designsystem.components.HbRadioGroup
import io.github.aequicor.heartbeat.designsystem.components.HbSearchField
import io.github.aequicor.heartbeat.designsystem.components.HbSecretField
import io.github.aequicor.heartbeat.designsystem.components.HbSegmentedControl
import io.github.aequicor.heartbeat.designsystem.components.HbStatusRow
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.components.HbTabs
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Catalog of the rendered kit: one screen per appearance and width. The screen is an executable
 * example of the documented contracts (HB-DS-039); a screenshot test renders it to an image for
 * review, so a component change is visible without launching the desktop application.
 */
@Composable
internal fun CatalogScreen(modifier: Modifier = Modifier) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.canvas)
            .padding(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.M),
    ) {
        CatalogSection("Палитра") { Palette(colors) }
        CatalogSection("Типографика") { TypographySamples(colors) }
        CatalogSection("Действия") { Actions(colors) }
        CatalogSection("Поля") { Fields() }
        CatalogSection("Выбор") { Selection() }
        CatalogSection("Навигация и списки") { NavigationAndLists(colors) }
        CatalogSection("Структура") { Structure() }
        CatalogSection("Метаданные") { Metadata(colors) }
        CatalogSection("Обратная связь") { Feedback(colors) }
        CatalogSection("Рабочая область") { Workspace(colors) }
        CatalogSection("Поверхности") { Surfaces(colors) }
    }
}

@Composable
private fun Structure() {
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

@Composable
private fun CatalogSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbThemeDefaults.LocalColors.current

    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
        BasicText(
            text = title,
            style = HbTypography.Title,
            color = { colors.text.primary },
        )
        content()
    }
}

@Composable
private fun Palette(colors: HbColors) {
    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
        SwatchRow(
            listOf(
                "Canvas" to colors.surface.canvas,
                "Base" to colors.surface.base,
                "Subtle" to colors.surface.subtle,
                "Raised" to colors.surface.raised,
                "Input" to colors.surface.input,
            ),
        )
        SwatchRow(
            listOf(
                "Text primary" to colors.text.primary,
                "Secondary" to colors.text.secondary,
                "Disabled" to colors.text.disabled,
                "Border subtle" to colors.border.subtle,
                "Border control" to colors.border.control,
            ),
        )
        SwatchRow(
            listOf(
                "Accent" to colors.accent.brand,
                "Accent text" to colors.accent.foreground,
                "Accent container" to colors.accent.container,
                "Focus" to colors.focus,
                "Selection" to colors.selection.background,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
            StatusPair("Success", colors.status.success.foreground, colors.status.success.container)
            StatusPair("Warning", colors.status.warning.foreground, colors.status.warning.container)
            StatusPair("Error", colors.status.error.foreground, colors.status.error.container)
            StatusPair("Info", colors.status.information.foreground, colors.status.information.container)
        }
    }
}

@Composable
private fun SwatchRow(swatches: List<Pair<String, Color>>) {
    val colors = HbThemeDefaults.LocalColors.current

    Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
        swatches.forEach { (name, color) ->
            Column(modifier = Modifier.width(SWATCH_WIDTH)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SWATCH_HEIGHT)
                        .clip(HbShapes.Small)
                        .background(color)
                        .border(1.dp, colors.border.subtle, HbShapes.Small),
                )
                BasicText(
                    text = name,
                    style = HbTypography.Metadata,
                    color = { colors.text.secondary },
                    modifier = Modifier.padding(top = HbSpacing.Xxs),
                )
            }
        }
    }
}

@Composable
private fun StatusPair(name: String, foreground: Color, container: Color) {
    Box(
        modifier = Modifier
            .clip(HbShapes.Small)
            .background(container)
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
    ) {
        BasicText(
            text = name,
            style = HbTypography.Caption,
            color = { foreground },
        )
    }
}

@Composable
private fun TypographySamples(colors: HbColors) {
    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs)) {
        BasicText("Заголовок страницы", style = HbTypography.PageTitle, color = { colors.text.primary })
        BasicText("Заголовок раздела", style = HbTypography.Title, color = { colors.text.primary })
        BasicText("Заголовок панели", style = HbTypography.PaneTitle, color = { colors.text.primary })
        BasicText("Текст для чтения: спокойный и читаемый.", style = HbTypography.Body, color = { colors.text.primary })
        BasicText("Подпись интерфейса", style = HbTypography.Label, color = { colors.text.secondary })
        BasicText("Мелкий текст", style = HbTypography.Caption, color = { colors.text.secondary })
        BasicText("fun main() = Unit", style = HbTypography.Code, color = { colors.text.primary })
        BasicText("12:45 · 128 токенов", style = HbTypography.Metadata, color = { colors.text.secondary })
    }
}

@Composable
private fun Actions(colors: HbColors) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HbButton(text = "Сохранить", onClick = {})
        HbButton(text = "Недоступно", onClick = {}, enabled = false)
        HbLink(text = "Открыть проект", onClick = {})
        HbCopyButton(label = "Копировать", copiedLabel = "Скопировано", onCopy = {})
        HbKeyHint(keys = listOf("Ctrl", "K"))
        BasicText(
            text = "Текст",
            style = HbTypography.Label,
            color = { colors.text.secondary },
        )
    }
}

@Composable
private fun Fields() {
    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
        HbFormField(label = "Заголовок", helpText = "Видно в списке диалогов") {
            HbTextField(
                value = "",
                onValueChange = {},
                placeholder = "Например, «Разбор релиза»",
            )
        }
        HbFormField(label = "Адрес сервера", errorMessage = "Проверьте адрес и повторите") {
            HbTextField(
                value = "https://",
                onValueChange = {},
                errorMessage = "Проверьте адрес и повторите",
            )
        }
        HbFormField(label = "Модель") {
            HbSelect(
                options = listOf("heartbeat-fast", "heartbeat-deep"),
                selected = "heartbeat-fast",
                onSelect = {},
                optionLabel = { it },
            )
        }
        HbFormField(label = "Подключение") {
            HbCombobox(
                options = listOf("Локальная среда", "Удалённый сервер"),
                query = "",
                placeholder = "Выберите подключение",
                onQueryChange = {},
                onSelect = {},
                optionLabel = { it },
                noResultsText = "Ничего не найдено",
            )
        }
        HbFormField(label = "Поиск") {
            HbSearchField(
                value = "heartbeat",
                onValueChange = {},
                clearDescription = "Очистить поиск",
                label = "Поиск",
            )
        }
        HbFormField(label = "Ключ доступа", helpText = "Хранится в профиле устройства") {
            HbSecretField(
                value = "sk-1234567890",
                onValueChange = {},
                label = "Ключ доступа",
                revealLabel = "Показать",
                hideLabel = "Скрыть",
            )
        }
        HbTextField(
            value = "Недоступное значение",
            onValueChange = {},
            label = "Отключено",
            enabled = false,
        )
    }
}

@Composable
private fun Selection() {
    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.M)) {
            HbCheckbox(label = "Не отмечено", checked = false, onCheckedChange = {})
            HbCheckbox(label = "Отмечено", checked = true, onCheckedChange = {})
            HbCheckbox(label = "Смешанное", checked = true, indeterminate = true, onCheckedChange = {})
            HbCheckbox(label = "Недоступно", checked = false, onCheckedChange = {}, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HbSpacing.M)) {
            HbSwitch(label = "Автозапуск", checked = true, onCheckedChange = {})
            HbSwitch(label = "Уведомления", checked = false, onCheckedChange = {})
        }
        HbRadioGroup(
            options = listOf("Точно", "Быстро"),
            selected = "Точно",
            onSelect = {},
            optionLabel = { it },
        )
        HbSegmentedControl(
            options = listOf("Диалог", "Проект", "Терминал"),
            selected = "Диалог",
            onSelect = {},
            optionLabel = { it },
        )
        HbTabs(
            tabs = listOf("История", "Настройки", "Модели"),
            selected = "История",
            onSelect = {},
            tabLabel = { it },
        )
    }
}

@Composable
private fun NavigationAndLists(colors: HbColors) {
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

@Composable
private fun Metadata(colors: HbColors) {
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

@Composable
private fun Feedback(colors: HbColors) {
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

private val SWATCH_WIDTH = 96.dp

private val SWATCH_HEIGHT = 28.dp

private val NAV_WIDTH = 360.dp

private val SURFACE_WIDTH = 320.dp

private val STRUCTURE_NAV_WIDTH = 220.dp

private val STRUCTURE_TREE_WIDTH = 380.dp

private val STRUCTURE_TABLE_WIDTH = 520.dp

private val SURFACE_HEIGHT = 180.dp

@Composable
private fun Workspace(colors: HbColors) {
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

@Composable
private fun Surfaces(colors: HbColors) {
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
