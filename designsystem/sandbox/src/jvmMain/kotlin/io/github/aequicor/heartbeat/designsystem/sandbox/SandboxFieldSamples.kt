package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbCheckbox
import io.github.aequicor.heartbeat.designsystem.components.HbCombobox
import io.github.aequicor.heartbeat.designsystem.components.HbCopyButton
import io.github.aequicor.heartbeat.designsystem.components.HbFormField
import io.github.aequicor.heartbeat.designsystem.components.HbKeyHint
import io.github.aequicor.heartbeat.designsystem.components.HbLink
import io.github.aequicor.heartbeat.designsystem.components.HbRadioGroup
import io.github.aequicor.heartbeat.designsystem.components.HbSearchField
import io.github.aequicor.heartbeat.designsystem.components.HbSecretField
import io.github.aequicor.heartbeat.designsystem.components.HbSegmentedControl
import io.github.aequicor.heartbeat.designsystem.components.HbSelect
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.components.HbTextArea
import io.github.aequicor.heartbeat.designsystem.components.HbTabs
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.foundation.HbSpace
import io.github.aequicor.heartbeat.designsystem.components.HbAvatar
import io.github.aequicor.heartbeat.designsystem.components.HbColumn
import io.github.aequicor.heartbeat.designsystem.components.HbFlowRow
import io.github.aequicor.heartbeat.designsystem.components.HbRow
import io.github.aequicor.heartbeat.designsystem.components.HbChip
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/** Action, field and selection samples of the sandbox (HB-DS-039). */
@Composable
internal fun Actions(colors: HbColors) {
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

/** Field samples, including the choose surfaces and the error state. */
@Composable
internal fun Fields() {
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
        SecurityFieldSamples()
    }
}

/** Checkbox, radio, switch, segmented and tab samples with their states. */
@Composable
internal fun Selection() {
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

/** Layout primitives with spacing tokens (HB-DS-009). */
@Composable
internal fun LayoutSamples(colors: HbColors) {
    Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.S)) {
        HbFlowRow(modifier = Modifier.width(FLOW_WIDTH)) {
            listOf("Принято", "В очереди", "Ошибка", "Три вложения", "Модель heartbeat-fast", "Проект").forEach {
                HbChip(label = it)
            }
        }
        HbRow(spacing = HbSpace.Six) {
            HbAvatar(initials = "HB", contentDescription = "Рабочая область")
            BasicText(
                text = "Строка с зазором из шкалы",
                style = HbTypography.Label,
                color = { colors.text.primary },
            )
        }
        HbColumn(spacing = HbSpace.Four) {
            BasicText("Первый пункт колонки", style = HbTypography.Label, color = { colors.text.primary })
            BasicText("Второй пункт колонки", style = HbTypography.Label, color = { colors.text.secondary })
        }
    }
}

private val FLOW_WIDTH = 420.dp

@Composable
private fun DisabledFieldSample(modifier: Modifier = Modifier) {
    HbTextField(
        value = "Недоступное значение",
        onValueChange = {},
        label = "Отключено",
        enabled = false,
        modifier = modifier,
    )
}

@Composable
private fun SecurityFieldSamples(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
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
        HbFormField(label = "Описание", helpText = "Видно в карточке диалога") {
            HbTextArea(
                value = "Короткое описание проекта и его ограничений.",
                onValueChange = {},
                label = "Описание",
            )
        }
        DisabledFieldSample()
    }
}
