@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.ui.text.font.FontFamily

internal object AuroraTestFonts {
    private val windowsTestHost = System.getProperty("os.name").startsWith("Windows")
    val ui = if (windowsTestHost) FontFamily("Segoe UI") else FontFamily.Default
    val code = if (windowsTestHost) FontFamily("Consolas") else FontFamily.Monospace
}
