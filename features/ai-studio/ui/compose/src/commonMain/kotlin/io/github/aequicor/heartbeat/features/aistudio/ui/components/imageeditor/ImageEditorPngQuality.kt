package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

internal enum class ImageEditorPngQuality(val label: String, val colorLevels: Int) {

    Original("Исходное", 0),
    Reduced("Сниженное", 6),
    Compact("Низкое", 4),
}
