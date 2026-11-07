@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbColors
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Catalog of the rendered kit: one screen per appearance and width. The screen is an executable
 * example of the documented contracts (HB-DS-039); a screenshot test renders it to an image for
 * review, and the catalog application shows it interactively.
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

private val SWATCH_WIDTH = 96.dp

private val SWATCH_HEIGHT = 28.dp
