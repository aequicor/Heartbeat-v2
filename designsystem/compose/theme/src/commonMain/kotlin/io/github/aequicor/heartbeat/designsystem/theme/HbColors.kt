package io.github.aequicor.heartbeat.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Color tokens of the Heartbeat application. */
data class HbColors(
    val primary: Color,
    val onPrimary: Color,
    val surface: Color,
    val onSurface: Color,
    val background: Color,
    val error: Color,
) {
    companion object {
        /** Default light palette. */
        val Default = HbColors(
            primary = Color(0xFF2E6FDB),
            onPrimary = Color(0xFFFFFFFF),
            surface = Color(0xFFF4F6FA),
            onSurface = Color(0xFF17191E),
            background = Color(0xFFFFFFFF),
            error = Color(0xFFB3261E),
        )
    }
}
