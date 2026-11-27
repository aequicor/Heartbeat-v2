package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.graphics.Color
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraImageEditor as Editor

internal enum class ImageEditorColor(val label: String, val color: Color) {

    Purple("Фиолетовый", Editor.AnnotationPurple),
    Black("Графит", Editor.AnnotationBlack),
    White("Белый", Editor.AnnotationWhite),
    Red("Красный", Editor.AnnotationRed),
    Blue("Синий", Editor.AnnotationBlue),
    Green("Зелёный", Editor.AnnotationGreen),
}
