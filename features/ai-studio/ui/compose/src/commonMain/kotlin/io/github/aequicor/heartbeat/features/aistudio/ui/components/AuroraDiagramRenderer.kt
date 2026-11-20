package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds

private val diagramQueue = Mutex()
private const val MaxDiagramSourceLength = 32_768
private val renderTimeout = 20.seconds
private const val MaxEncodedPngLength = 3_000_000
private const val PngResultPrefix = "ok:"

@Composable
internal expect fun BindAuroraDiagramContext()

internal suspend fun renderAuroraDiagram(format: String, source: String): ImageBitmap {
    val language = format.lowercase()
    require(language == "mermaid" || language == "plantuml") { "Unknown diagram format" }
    require(source.isNotBlank() && source.length <= MaxDiagramSourceLength) { "Invalid diagram source size" }
    return diagramQueue.withLock {
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(renderTimeout) { renderAuroraDiagramOnPlatform(language, source) }
                ?: error("Diagram renderer timed out")
        }
    }
}

internal object DiagramWeb {
    const val Origin = "https://aurora-diagram.invalid"
    const val Entry = "$Origin/index.html"
    const val ContentPolicy = "default-src 'none'; " +
        "script-src 'self' 'wasm-unsafe-eval'; style-src 'unsafe-inline'; " +
        "img-src blob: data:; worker-src 'self' blob:; " +
        "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
}

private const val DiagramAssetPath = "files/aurora-diagram/"
private val diagramEngineAssets = mapOf(
    "index.html" to "text/html",
    "renderer.js" to "text/javascript",
    "mermaid.min.js" to "text/javascript",
    "plantuml.js" to "text/javascript",
    "viz-global.js" to "text/javascript",
    "themes.js" to "text/javascript",
    "openiconic.js" to "text/javascript",
    "emoji.js" to "text/javascript",
    "c4.min.js" to "text/javascript",
)

internal class DiagramAsset(val mimeType: String, val bytes: ByteArray)

@OptIn(ExperimentalResourceApi::class)
internal suspend fun readDiagramAsset(url: String): DiagramAsset? {
    val prefix = "${DiagramWeb.Origin}/"
    if (!url.startsWith(prefix)) return null
    val name = url.removePrefix(prefix)
    return diagramEngineAssets[name]?.let { mimeType ->
        DiagramAsset(mimeType, Res.readBytes(DiagramAssetPath + name))
    }
}

internal fun diagramRequest(format: String, source: String): String =
    "window.startAuroraDiagram('$format', '${Base64.encode(source.encodeToByteArray())}')"

internal fun decodeDiagramResult(result: String): ByteArray {
    check(result.startsWith(PngResultPrefix) && result.length <= MaxEncodedPngLength + PngResultPrefix.length) {
        "Diagram preview failed"
    }
    return Base64.decode(result.removePrefix(PngResultPrefix))
}

internal expect suspend fun renderAuroraDiagramOnPlatform(format: String, source: String): ImageBitmap
