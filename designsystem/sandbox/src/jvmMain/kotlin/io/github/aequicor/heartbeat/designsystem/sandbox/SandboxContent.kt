@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbDialog
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbTooltip
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Sandbox of the rendered kit: one screen per appearance and width. The screen is an executable
 * example of the documented contracts (HB-DS-039); a screenshot test renders it to an image for
 * review, and the sandbox application shows it interactively.
 */
@Composable
internal fun SandboxScreen(modifier: Modifier = Modifier) {
    val colors = HbTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface.canvas)
            .verticalScroll(rememberScrollState())
            .padding(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.M),
    ) {
        SandboxSection("Палитра") { Palette(colors) }
        SandboxSection("Типографика") { TypographySamples(colors) }
        SandboxSection("Иконки") { Icons(colors) }
        SandboxSection("Действия") { Actions(colors) }
        SandboxSection("Поля") { Fields() }
        SandboxSection("Выбор") { Selection() }
        SandboxSection("Расширенный набор") { ExtendedKit(colors) }
        SandboxSection("Раскладка") { LayoutSamples(colors) }
        SandboxSection("Навигация и списки") { NavigationAndLists(colors) }
        SandboxSection("Структура") { Structure() }
        SandboxSection("Метаданные") { Metadata(colors) }
        SandboxSection("Обратная связь") { Feedback(colors) }
        SandboxSection("Рабочая область") { Workspace(colors) }
        SandboxSection("Поверхности") { Surfaces(colors) }
        SandboxSection("Диалоги и подсказки") { DialogsAndPopups(colors) }
    }
}

@Composable
private fun SandboxSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbTheme.colors

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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
            StatusPair("Success", colors.status.success.foreground, colors.status.success.container)
            StatusPair("Warning", colors.status.warning.foreground, colors.status.warning.container)
            StatusPair("Error", colors.status.error.foreground, colors.status.error.container)
            StatusPair("Info", colors.status.information.foreground, colors.status.information.container)
        }
    }
}

@Composable
private fun SwatchRow(swatches: List<Pair<String, Color>>) {
    val colors = HbTheme.colors

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
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
        BasicText(
            "Datenschutzerklärung: Die Verarbeitung personenbezogener Daten erfolgt ausschließlich " +
                "nach der Einwilligung der betroffenen Person.",
            style = HbTypography.Body,
            color = { colors.text.primary },
        )
    }
}

private val SWATCH_WIDTH = 96.dp

private val SWATCH_HEIGHT = 28.dp

/** Dialog open/close and tooltip trigger examples, verified interactively (HB-DS-028, HB-DS-029). */
@Composable
private fun DialogsAndPopups(colors: HbColors) {
    var showDialog by remember { mutableStateOf(false) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        HbButton(text = "Открыть диалог", onClick = { showDialog = true })
        HbTooltip(text = "Копирует выбранный текст в буфер обмена") {
            HbButton(text = "С подсказкой", onClick = {})
        }
    }

    if (showDialog) {
        HbDialog(
            title = "Удалить диалог?",
            onDismissRequest = { showDialog = false },
            actions = {
                HbButton(text = "Отмена", onClick = { showDialog = false }, variant = HbButtonVariant.Ghost)
                HbButton(
                    text = "Удалить",
                    onClick = { showDialog = false },
                    variant = HbButtonVariant.Destructive,
                )
            },
        ) {
            BasicText(
                text = "Это действие нельзя отменить. Черновик и история диалога будут удалены.",
                style = HbTypography.Body,
                color = { colors.text.primary },
            )
        }
    }
}

@Composable
private fun Icons(colors: HbColors) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        HbIconGlyph.entries.forEach { glyph ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HbIcon(glyph = glyph, size = 24.dp, tint = colors.text.primary)
                    HbIcon(glyph = glyph, size = 18.dp, tint = colors.text.primary)
                }
                BasicText(
                    text = glyph.name,
                    style = HbTypography.Metadata,
                    color = { colors.text.secondary },
                )
            }
        }
    }
}
