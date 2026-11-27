package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult

internal sealed interface ImageEditAnnotation {

    data class Label(val at: Offset, val layout: TextLayoutResult) : ImageEditAnnotation

    data class Brush(val points: List<Offset>, val color: Color, val width: Float) : ImageEditAnnotation

    data class Shape(
        val kind: Kind,
        val start: Offset,
        val end: Offset,
        val color: Color,
        val width: Float,
        val fill: Color?,
    ) : ImageEditAnnotation {

        enum class Kind(val label: String) {
            Rectangle("Прямоугольник"),
            Ellipse("Эллипс"),
            Arrow("Стрелка"),
        }
    }
}
