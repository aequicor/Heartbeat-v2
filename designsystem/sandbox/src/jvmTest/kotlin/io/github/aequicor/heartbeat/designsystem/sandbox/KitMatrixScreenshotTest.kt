@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbCheckbox
import io.github.aequicor.heartbeat.designsystem.components.HbRadioGroup
import io.github.aequicor.heartbeat.designsystem.components.HbSegmentedControl
import io.github.aequicor.heartbeat.designsystem.components.HbSelect
import io.github.aequicor.heartbeat.designsystem.components.HbSettingsRow
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.components.HbTabs
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Boundary matrix of the kit (HB-DS-031, HB-DS-039): the documented widths 320, 390, 719, 720,
 * 834, 1199, 1200, 1440 and 1920, the low heights 480 and 639, the comfortable density and the RTL
 * layout are rendered from one component strip. Each image is a golden, so a control that clips,
 * squeezes or loses its minimum target at a boundary is visible in the review and fails CI.
 *
 * The strip is intentionally smaller than the full catalog: the catalog checks every state in one
 * long screen, while this matrix isolates the adaptive geometry at each boundary.
 */
@OptIn(ExperimentalComposeUiApi::class)
class KitMatrixScreenshotTest {

    @Test
    fun rendersBoundaryWidths() {
        BOUNDARY_WIDTHS.forEach { width ->
            render(name = "kit-width-$width", width = width, height = STRIP_HEIGHT)
        }
    }

    @Test
    fun rendersLowHeights() {
        LOW_HEIGHTS.forEach { height ->
            render(name = "kit-height-$height", width = WIDE_WIDTH, height = height)
        }
    }

    @Test
    fun rendersComfortableDensity() {
        render(
            name = "kit-comfortable",
            width = WIDE_WIDTH,
            height = STRIP_HEIGHT,
            density = HbDensity.Comfortable,
        )
    }

    @Test
    fun rendersRtl() {
        render(name = "kit-rtl", width = 720, height = STRIP_HEIGHT, rtl = true)
    }

    private fun render(
        name: String,
        width: Int,
        height: Int,
        density: HbDensity = HbDensity.Dense,
        rtl: Boolean = false,
    ) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                HbTheme(appearance = HbAppearance.Light, density = density) {
                    KitStrip()
                }
            }
        }

        try {
            val data = scene.render().encodeToData(EncodedImageFormat.PNG)
            assertTrue(data != null, "PNG encoding failed for $name")
            GoldenImage.verify(name = name, png = data.bytes)
            GoldenImage.assertNoHorizontalClipping(name = name, png = data.bytes)
        } finally {
            scene.close()
        }
    }

    private companion object {
        private val BOUNDARY_WIDTHS = listOf(320, 390, 719, 720, 834, 1199, 1200, 1440, 1920)

        private val LOW_HEIGHTS = listOf(480, 639)

        private const val WIDE_WIDTH = 1200

        private const val STRIP_HEIGHT = 640
    }
}

/** Representative controls of the kit; the matrix checks their geometry at each boundary. */
@Composable
private fun KitStrip() {
    val colors = HbTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface.canvas)
            .padding(HbSpacing.M),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        ActionStrip()
        FieldStrip()
        ChoiceStrip()
        HbSettingsRow(
            title = "Автозапуск",
            description = "Запускать Heartbeat при входе",
            control = { HbSwitch(label = "", checked = true, onCheckedChange = {}) },
        )
        BasicText(
            text = "Длинная локализованная подпись действия, которая переносится",
            style = HbTypography.Label,
            color = { colors.text.primary },
            modifier = Modifier.padding(top = HbSpacing.Xxs),
        )
    }
}

@Composable
private fun ActionStrip() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        HbButton(text = "Сохранить", onClick = {})
        HbButton(text = "Настроить", onClick = {}, variant = HbButtonVariant.Secondary)
        HbButton(text = "Удалить", onClick = {}, variant = HbButtonVariant.Destructive)
    }
}

@Composable
private fun FieldStrip() {
    HbTextField(
        value = "",
        onValueChange = {},
        placeholder = "Например, «Разбор релиза»",
    )
    HbSelect(
        options = listOf("heartbeat-fast", "heartbeat-deep"),
        selected = "heartbeat-fast",
        onSelect = {},
        optionLabel = { it },
    )
}

@Composable
private fun ChoiceStrip() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        HbCheckbox(label = "Согласен", checked = true, onCheckedChange = {})
        HbSwitch(label = "Автозапуск", checked = true, onCheckedChange = {})
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
        tabs = listOf("История", "Настройки"),
        selected = "История",
        onSelect = {},
        tabLabel = { it },
    )
}
