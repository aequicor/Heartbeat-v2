package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.geometry.Offset

internal data class ImageEditorToolSettings(
    val aspect: ImageCropAspect = ImageCropAspect.Free,
    val font: ImageEditorFont = ImageEditorFont.Golos,
    val textSize: Int = 16,
    val textColor: ImageEditorColor = ImageEditorColor.Purple,
    val brushColor: ImageEditorColor = ImageEditorColor.Purple,
    val brushWidth: Int = 3,
    val shape: ImageEditAnnotation.Shape.Kind = ImageEditAnnotation.Shape.Kind.Rectangle,
    val shapeColor: ImageEditorColor = ImageEditorColor.Purple,
    val shapeWidth: Int = 3,
    val fill: ImageEditorColor? = null,
) {

    fun brush(points: List<Offset>): ImageEditAnnotation.Brush =
        ImageEditAnnotation.Brush(points, brushColor.color, brushWidth.toFloat())

    fun shape(start: Offset, last: Offset): ImageEditAnnotation.Shape =
        ImageEditAnnotation.Shape(shape, start, last, shapeColor.color, shapeWidth.toFloat(), fill?.color)
}
