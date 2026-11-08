package io.github.aequicor.heartbeat.designsystem.consumer

import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbButtonVariant
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbLazyList
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.components.HbScrollArea
import io.github.aequicor.heartbeat.designsystem.components.HbSettingsRow
import io.github.aequicor.heartbeat.designsystem.components.HbSidebar
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbContrastPreference
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

/**
 * Compilation probe of an external consumer.
 *
 * The module depends only on the component kit, yet uses Foundation types (`HbAppearance`,
 * `HbDensity`, `HbMotionPreference`, `HbIconGlyph`, `HbContrastPreference`) and theme types. If a
 * dependency of the kit is declared as `implementation`, this file stops compiling and the build
 * reports the broken public API before a consumer does.
 *
 * The probe also instantiates every family of the documented inventory (HB-DS components): actions
 * with their variants, fields, a menu, a lazy list, a scroll area, settings and the workspace
 * sidebar. A signature change that breaks a consumer fails the `consumer-compilation` gate.
 */
@Composable
internal fun ConsumerScreen() {
    HbTheme(
        appearance = HbAppearance.Dark,
        motion = HbMotionPreference.Reduced,
        density = HbDensity.Touch,
        contrast = HbContrastPreference.High,
    ) {
        HbText(text = "Проверка потребителя")
        HbIcon(glyph = HbIconGlyph.Search)
        HbButton(text = "Действие", onClick = {})
        HbButton(text = "Удалить", onClick = {}, variant = HbButtonVariant.Destructive)
        HbTextField(value = "", onValueChange = {}, readOnly = true)
        HbMenu(
            expanded = false,
            onDismissRequest = {},
            actions = listOf(HbMenuAction(label = "Открыть", onClick = {})),
        ) {
            HbButton(text = "Меню", onClick = {})
        }
        HbLazyList(items = listOf("Первый"), key = { it }) { item -> HbText(text = item) }
        HbScrollArea { HbText(text = "Прокрутка") }
        HbSettingsRow(
            title = "Настройка",
            description = "Пояснение",
            control = { HbSwitch(label = "", checked = true, onCheckedChange = {}) },
        )
        HbSidebar(title = "Диалоги") { HbText(text = "Раздел") }
    }
}
