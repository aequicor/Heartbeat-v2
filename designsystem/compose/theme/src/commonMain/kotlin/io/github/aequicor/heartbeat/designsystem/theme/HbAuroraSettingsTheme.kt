package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.aequicor.heartbeat.designsystem.foundation.HbColorPalettes

@Composable
fun HbAuroraSettingsTheme(content: @Composable () -> Unit) {
    val current = HbTheme.colors
    val palette = remember(current) {
        if (current == HbColorPalettes.Light.toHbColors()) {
            current.copy(
                surface = current.surface.copy(subtle = HbAuroraColors.WorkHover, raised = HbAuroraColors.Pearl),
                border = current.border.copy(
                    subtle = HbAuroraColors.HeaderOutline,
                    control = HbAuroraColors.HeaderAccessOutline,
                ),
                action = current.action.copy(primaryBackground = HbAuroraColors.DesktopPrimaryAction),
                accent = current.accent.copy(
                    brand = HbAuroraColors.Primary,
                    foreground = HbAuroraColors.DesktopPrimaryAction,
                ),
                selection = current.selection.copy(background = HbAuroraColors.Current),
            )
        } else current
    }
    val geometry = HbThemeDefaults.LocalGeometry.current
    val controlHeight = maxOf(geometry.controlHeight, HbEngineSettingsLayout.fieldHeight.value.toInt())
    CompositionLocalProvider(
        HbThemeDefaults.LocalColors provides palette,
        HbThemeDefaults.LocalGeometry provides geometry.copy(controlHeight = controlHeight),
        content = content,
    )
}
