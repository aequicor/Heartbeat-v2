package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import io.github.aequicor.heartbeat.designsystem.foundation.HbAppearance
import io.github.aequicor.heartbeat.designsystem.foundation.HbDensity
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotionPreference
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme

/** Uses the same host font families as the desktop entry point. */
@Composable
internal fun AuroraTestTheme(
    appearance: HbAppearance = HbAppearance.Light,
    motion: HbMotionPreference = HbMotionPreference.System,
    textScale: Float = 1f,
    density: HbDensity = HbDensity.Dense,
    fontFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    HbTheme(
        appearance = appearance,
        motion = motion,
        textScale = textScale,
        density = density,
        fontFamily = fontFamily ?: AuroraTestFonts.ui,
        codeFontFamily = AuroraTestFonts.code,
        content = content,
    )
}
