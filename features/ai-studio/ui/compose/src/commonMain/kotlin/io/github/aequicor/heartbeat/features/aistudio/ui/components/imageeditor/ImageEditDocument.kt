package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageEditor

internal data class ImageEditDocument(
    val size: IntSize,
    val description: ImageEditor,
    val fromOriginal: ImageEditTransform = ImageEditTransform(),
    val layers: List<Layer> = emptyList(),
) {

    data class Layer(val annotation: ImageEditAnnotation, val toOriginal: ImageEditTransform)

    fun annotate(annotation: ImageEditAnnotation): ImageEditDocument =
        copy(layers = layers + Layer(annotation, fromOriginal.inverse()))

    fun crop(rect: IntRect): ImageEditDocument = copy(
        size = rect.size,
        fromOriginal = ImageEditTransform(origin = Offset(-rect.left.toFloat(), -rect.top.toFloat())).after(fromOriginal),
        description = description.crop(rect.left, rect.top, rect.width, rect.height),
    )

    fun rotateRight(): ImageEditDocument = copy(
        size = IntSize(size.height, size.width),
        fromOriginal = ImageEditTransform(1, Offset(size.height.toFloat(), 0f)).after(fromOriginal),
        description = description.rotateRight(size.height),
    )
}
