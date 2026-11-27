package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import io.github.aequicor.heartbeat.designsystem.components.HbAuroraFont
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.ibm_plex_mono
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.pt_serif
import org.jetbrains.compose.resources.Font

internal enum class ImageEditorFont(val label: String) {

    Golos("Golos Text"),
    Serif("PT Serif"),
    Mono("IBM Plex Mono"),
    ;

    @Composable
    fun fontFamily(): FontFamily = when (this) {
        Golos -> HbAuroraFont()
        Serif -> FontFamily(Font(Res.font.pt_serif))
        Mono -> FontFamily(Font(Res.font.ibm_plex_mono))
    }
}
