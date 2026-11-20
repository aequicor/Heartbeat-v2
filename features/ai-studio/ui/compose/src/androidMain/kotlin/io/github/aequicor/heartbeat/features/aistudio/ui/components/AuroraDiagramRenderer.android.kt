package io.github.aequicor.heartbeat.features.aistudio.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_OK
import kotlin.coroutines.resume

@Volatile private var diagramApplication: Context? = null
private var androidDiagramBrowser: AndroidDiagramBrowser? = null

@Composable
internal actual fun BindAuroraDiagramContext() {
    val application = LocalContext.current.applicationContext
    SideEffect { diagramApplication = application }
}

internal actual suspend fun renderAuroraDiagramOnPlatform(format: String, source: String): ImageBitmap {
    val application = checkNotNull(diagramApplication) { "Diagram renderer is unavailable" }
    val browser = withContext(Dispatchers.Main.immediate) {
        androidDiagramBrowser?.takeUnless { it.closed }
            ?: AndroidDiagramBrowser(application).also { androidDiagramBrowser = it }
    }
    val result = try {
        browser.render(diagramRequest(format, source))
    } catch (canceled: CancellationException) {
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            browser.close()
            androidDiagramBrowser = null
        }
        throw canceled
    }
    val bytes = decodeDiagramResult(result)
    val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Invalid diagram image" }
    return bitmap.asImageBitmap()
}

@SuppressLint("SetJavaScriptEnabled") // Only the compiled-in renderer origin is served to this WebView.
private class AndroidDiagramBrowser(context: Context) {
    private val ready = CompletableDeferred<Unit>()
    var closed: Boolean = false
        private set

    private val view = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            @Deprecated("Required for WebViews before API 24")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val asset = if (request.method == "GET") {
                    runBlocking { readDiagramAsset(request.url.toString()) }
                } else {
                    null
                }
                return WebResourceResponse(
                    asset?.mimeType ?: "text/plain",
                    "UTF-8",
                    if (asset == null) HTTP_FORBIDDEN else HTTP_OK,
                    if (asset == null) "Forbidden" else "OK",
                    mapOf("Content-Security-Policy" to DiagramWeb.ContentPolicy, "X-Content-Type-Options" to "nosniff"),
                    ByteArrayInputStream(asset?.bytes ?: byteArrayOf()),
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url == DiagramWeb.Entry) ready.complete(Unit)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                ready.completeExceptionally(IllegalStateException("Diagram renderer stopped"))
                close()
                return true
            }
        }
        loadUrl(DiagramWeb.Entry)
    }

    suspend fun render(script: String): String {
        ready.await()
        evaluate("void $script")
        val result = StringBuilder()
        while (true) {
            val chunk = evaluate("window.takeAuroraDiagramChunk(${result.length}, $ChunkSize)")
            if (chunk == "null") {
                delay(ResultPollingMillis)
                continue
            }
            // The script returns only ASCII markers and base64, so the JSON quotes are the only escaping.
            check(chunk.startsWith('"') && chunk.endsWith('"')) { "Invalid diagram result" }
            val content = chunk.substring(1, chunk.length - 1)
            result.append(content)
            if (content.length < ChunkSize) return result.toString()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        view.destroy()
    }

    private suspend fun evaluate(script: String): String = withContext(Dispatchers.Main.immediate) {
        check(!closed) { "Diagram renderer stopped" }
        suspendCancellableCoroutine { callback ->
            view.evaluateJavascript(script) { callback.resume(it) }
        }
    }

    private companion object {
        const val ChunkSize = 24 * 1024
        const val ResultPollingMillis = 30L
    }
}
