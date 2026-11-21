package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Every visible row in one chat shares this bounded cache, including its modal viewer.
@Suppress("CompositionLocalAllowlist", "NoNonPrivateGlobalVariables")
internal val LocalDiagramPreviews = staticCompositionLocalOf<AuroraDiagramPreviews?> { null }

/** The visible row owns the rendering job. Only finished previews survive row disposal. */
internal class AuroraDiagramPreviews(
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val render: suspend (String, String, Float) -> ImageBitmap = ::renderAuroraDiagram,
) {
    constructor(
        worker: CoroutineDispatcher = Dispatchers.Default,
        render: suspend (String, String) -> ImageBitmap,
    ) : this(worker, { format, source, _ -> render(format, source) })

    private data class Source(val format: String, val text: String, val scale: Float)

    private val rendering = Mutex()
    private val previews = linkedMapOf<Source, ImageBitmap>()
    private var bytes = 0L

    suspend fun preview(format: String, text: String, pixelScale: Float = 2f): ImageBitmap = withContext(worker) {
        rendering.withLock {
            val key = Source(format, text, pixelScale)
            previews.remove(key)?.let { hit ->
                previews[key] = hit
                return@withLock hit
            }
            val bitmap = render(format, text, pixelScale)
            val cost = sizeOf(key, bitmap)
            if (cost <= MaxBytes) {
                while (previews.isNotEmpty() && (bytes + cost > MaxBytes || previews.size >= MaxEntries)) {
                    val oldest = previews.entries.first()
                    bytes -= sizeOf(oldest.key, oldest.value)
                    previews.remove(oldest.key)
                }
                previews[key] = bitmap
                bytes += cost
            }
            bitmap
        }
    }

    private fun sizeOf(key: Source, bitmap: ImageBitmap): Long =
        bitmap.width.toLong() * bitmap.height * RgbaBytes + key.text.length.toLong() * 2

    private companion object {
        const val RgbaBytes = 4
        const val MaxEntries = 32
        const val MaxBytes = 24L * 1024 * 1024
    }
}
