package io.github.aequicor.heartbeat.designsystem.consumer

import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbIcon
import io.github.aequicor.heartbeat.designsystem.components.HbText
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
    }
}
