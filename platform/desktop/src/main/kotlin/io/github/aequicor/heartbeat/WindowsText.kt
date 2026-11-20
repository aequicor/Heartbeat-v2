package io.github.aequicor.heartbeat

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import java.awt.Container
import java.awt.RenderingHints
import java.awt.Toolkit
import org.jetbrains.skia.PixelGeometry
import org.jetbrains.skiko.SkiaLayer

@OptIn(ExperimentalTextApi::class)
internal fun configureWindowsText(
    window: ComposeWindow,
    preferSubpixel: Boolean = System.getenv("HEARTBEAT_WINDOWS_SUBPIXEL_TEXT") != "0",
    pixelGeometry: PixelGeometry? = windowsSubpixelGeometry(),
): PlatformTextStyle? {
    if (!System.getProperty("os.name").startsWith("Windows")) return null
    // Match the desktop's ClearType orientation. Unknown layouts and an explicit opt-out
    // retain neutral grayscale edges, including screens without a supported LCD layout.
    val smoothing = if (!preferSubpixel || pixelGeometry == null ||
        pixelGeometry !in listOf(PixelGeometry.RGB_H, PixelGeometry.BGR_H)
    ) {
        println("Heartbeat: Windows grayscale text enabled")
        FontSmoothing.AntiAlias
    } else {
        configureSubpixelSmoothing(window, pixelGeometry)
    }
    return windowsTextStyle(smoothing)
}

@OptIn(ExperimentalTextApi::class)
private fun configureSubpixelSmoothing(window: ComposeWindow, pixelGeometry: PixelGeometry): FontSmoothing {
    val layer = window.contentPane.components.filterIsInstance<Container>()
        .flatMap { it.components.asList() }
        .filterIsInstance<SkiaLayer>()
        .singleOrNull()
    return if (layer != null && !layer.isDisplayable && layer.setTextPixelGeometry(pixelGeometry)) {
        println("Heartbeat: Windows subpixel text enabled ($pixelGeometry)")
        FontSmoothing.SubpixelAntiAlias
    } else {
        System.err.println("Heartbeat: Skia layer unavailable for subpixel text; using grayscale")
        FontSmoothing.AntiAlias
    }
}

private fun windowsSubpixelGeometry(): PixelGeometry? {
    if (!System.getProperty("os.name").startsWith("Windows")) return null
    // AWT exposes the desktop-wide ClearType orientation at startup.
    val hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") as? Map<*, *>
    return when (hints?.get(RenderingHints.KEY_TEXT_ANTIALIASING)) {
        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB -> PixelGeometry.RGB_H
        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR -> PixelGeometry.BGR_H
        else -> null
    }
}

@OptIn(ExperimentalTextApi::class)
private fun windowsTextStyle(smoothing: FontSmoothing): PlatformTextStyle {
    val defaults = FontRasterizationSettings.PlatformDefault
    return PlatformTextStyle(
        spanStyle = null,
        paragraphStyle = PlatformParagraphStyle(
            fontRasterizationSettings = FontRasterizationSettings(
                smoothing = smoothing,
                hinting = defaults.hinting,
                // Snapped glyph positions match the desktop reference more closely at small sizes.
                // Skia still uses DirectWrite; this does not select Qt's GDI font engine.
                subpixelPositioning = false,
                autoHintingForced = defaults.autoHintingForced,
            ),
        ),
    )
}

// Compose 1.12.2 does not pass a pixel geometry to its SkiaLayer constructor. Set the field
// before the layer is attached, while Skiko has yet to create the surface for this window.
private fun SkiaLayer.setTextPixelGeometry(geometry: PixelGeometry): Boolean = try {
    val field = SkiaLayer::class.java.getDeclaredField("pixelGeometry")
    field.trySetAccessible() && run {
        field.set(this, geometry)
        pixelGeometry == geometry
    }
} catch (_: ReflectiveOperationException) {
    false
}
