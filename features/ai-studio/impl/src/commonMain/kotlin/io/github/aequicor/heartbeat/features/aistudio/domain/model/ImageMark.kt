package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ImageMark(val x: Int, val y: Int, val name: String, val description: String) {

    fun crop(left: Int, top: Int, width: Int, height: Int): ImageMark? {
        val nextX = x - left
        val nextY = y - top
        return if (nextX in 0 until width && nextY in 0 until height) copy(x = nextX, y = nextY) else null
    }

    fun rotateRight(height: Int): ImageMark = copy(x = height - 1 - y, y = x)
}
