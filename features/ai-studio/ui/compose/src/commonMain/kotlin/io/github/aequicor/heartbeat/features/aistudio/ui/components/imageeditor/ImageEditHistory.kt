package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

internal data class ImageEditHistory(
    val original: ImageEditDocument,
    val current: ImageEditDocument = original,
    private val undo: List<ImageEditDocument> = emptyList(),
    private val redo: List<ImageEditDocument> = emptyList(),
) {

    val changed: Boolean get() = current != original

    val canUndo: Boolean get() = undo.isNotEmpty()

    val canRedo: Boolean get() = redo.isNotEmpty()

    fun commit(document: ImageEditDocument): ImageEditHistory =
        if (document == current) this else copy(current = document, undo = undo + current, redo = emptyList())

    fun undo(): ImageEditHistory =
        if (!canUndo) this else copy(current = undo.last(), undo = undo.dropLast(1), redo = redo + current)

    fun redo(): ImageEditHistory =
        if (!canRedo) this else copy(current = redo.last(), undo = undo + current, redo = redo.dropLast(1))
}
