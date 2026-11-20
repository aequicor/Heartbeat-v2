package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandler.TerminationStatus
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefRenderHandler
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.handler.CefScreenInfo
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import org.jetbrains.skia.Image
import java.awt.Canvas
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import java.lang.reflect.Proxy
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_OK
import java.util.concurrent.atomic.AtomicReference

@Composable
internal actual fun BindAuroraDiagramContext() {
    // Desktop initializes the browser on first use.
}

internal actual suspend fun renderAuroraDiagramOnPlatform(format: String, source: String): ImageBitmap {
    val result = try {
        DesktopDiagramRenderer.render(diagramRequest(format, source))
    } catch (unavailable: LinkageError) {
        throw IllegalStateException("Diagram browser is unavailable", unavailable)
    } catch (unavailable: ReflectiveOperationException) {
        throw IllegalStateException("Diagram browser is unavailable", unavailable)
    }
    return Image.makeFromEncoded(decodeDiagramResult(result)).use { it.toComposeImageBitmap() }
}

private object DesktopDiagramRenderer {
    private val application by lazy {
        JetBrainsDiagramBrowser.start().also { cef ->
            Runtime.getRuntime().addShutdownHook(Thread({ cef.dispose() }, "aurora-diagram-shutdown"))
        }
    }
    private var host: DesktopDiagramBrowser? = null

    suspend fun render(script: String): String {
        try {
            val browser = withContext(Dispatchers.IO) {
                host ?: DesktopDiagramBrowser(application).also { host = it }
            }
            return browser.render(script)
        } catch (canceled: CancellationException) {
            val discarded = host
            host = null
            discarded?.close()
            throw canceled
        } catch (failure: IllegalStateException) {
            host?.close()
            host = null
            throw failure
        }
    }
}

private class DesktopDiagramBrowser(application: CefApp) {
    private val ready = CompletableDeferred<Unit>()
    private val response = AtomicReference<CompletableDeferred<String>?>(null)
    private val client = application.createClient()
    private val browser: CefBrowser

    init {
        val bridge = CefMessageRouter.CefMessageRouterConfig("auroraDiagramResult", "cancelAuroraDiagramResult")
        val router = CefMessageRouter.create(bridge, object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser,
                frame: CefFrame,
                queryId: Long,
                request: String,
                persistent: Boolean,
                callback: CefQueryCallback,
            ): Boolean {
                if (!frame.isMain || frame.url != DiagramWeb.Entry || persistent) return false
                response.getAndSet(null)?.complete(request)
                callback.success("")
                return true
            }
        })
        client.addMessageRouter(router)
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain && frame.url == DiagramWeb.Entry && httpStatusCode == HTTP_OK) ready.complete(Unit)
            }
        })
        client.addRequestHandler(DiagramRequestHandler { failure ->
            ready.completeExceptionally(failure)
            response.getAndSet(null)?.completeExceptionally(failure)
        })
        browser = JetBrainsDiagramBrowser.create(client)
        browser.createImmediately()
    }

    suspend fun render(script: String): String {
        ready.await()
        val result = CompletableDeferred<String>()
        check(response.compareAndSet(null, result)) { "Diagram renderer busy" }
        browser.executeJavaScript(script, DiagramWeb.Entry, 0)
        return result.await()
    }

    fun close() {
        browser.close(true)
        client.dispose()
    }
}

/**
 * JBR adds an out-of-process browser and an offscreen handler to the upstream JCEF API.
 * Resolve those extensions only for diagram preview so plain JBR can still run Aurora.
 */
private object JetBrainsDiagramBrowser {
    private const val SurfaceSize = 512
    private const val ScreenDepth = 24
    private const val ChannelDepth = 8

    fun start(): CefApp {
        val javaHome = File(System.getProperty("java.home"))
        val app = when {
            System.getProperty("os.name").startsWith("Mac") ->
                javaHome.parentFile.resolve("Frameworks/cef_server.app/Contents/MacOS/cef_server")
            System.getProperty("os.name").startsWith("Windows") -> javaHome.resolve("bin/cef_server.exe")
            else -> javaHome.resolve("lib/cef_server")
        }
        check(app.isFile) { "Diagram browser executable is unavailable" }
        System.setProperty("jcef.remote.enabled", "true")
        check(CefApp::class.java.getMethod("isRemoteEnabled").invoke(null) == true) {
            "Out-of-process diagram browser is unavailable"
        }
        val settings = CefSettings().apply {
            windowless_rendering_enabled = true
            log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
        }
        return CefApp::class.java.getMethod(
            "getInstance", Array<String>::class.java, CefSettings::class.java, File::class.java,
        ).invoke(null, arrayOf("--disable-background-networking", "--no-proxy-server"), settings, null) as CefApp
    }

    fun create(client: CefClient): CefBrowser {
        val surface = Canvas().apply { setSize(SurfaceSize, SurfaceSize) }
        val bounds = Rectangle(0, 0, surface.width, surface.height)
        val nativeHandler = Class.forName("org.cef.handler.CefNativeRenderHandler")
        val handler = Proxy.newProxyInstance(nativeHandler.classLoader, arrayOf(nativeHandler)) { proxy, method, args ->
            when (method.name) {
                "getViewRect" -> bounds
                "getScreenPoint" -> args[1] as? Point ?: Point()
                "getScreenInfo" -> {
                    (args[1] as CefScreenInfo).Set(1.0, ScreenDepth, ChannelDepth, false, bounds, bounds)
                    true
                }
                "getDeviceScaleFactor" -> 1.0
                "onCursorChange", "startDragging" -> false
                "equals" -> proxy === args[0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Aurora diagram render handler"
                else -> null
            }
        }
        val rendering = Class.forName("org.cef.browser.CefRendering")
        val withHandler = Class.forName("org.cef.browser.CefRendering\$CefRenderingWithHandler")
            .getConstructor(CefRenderHandler::class.java, Component::class.java)
            .newInstance(handler, surface)
        return CefClient::class.java.getMethod("createBrowser", String::class.java, rendering, Boolean::class.java)
            .invoke(client, DiagramWeb.Entry, withHandler, false) as CefBrowser
    }
}

private class DiagramRequestHandler(
    private val onFailure: (IllegalStateException) -> Unit,
) : CefRequestHandlerAdapter() {
    override fun onBeforeBrowse(
        browser: CefBrowser,
        frame: CefFrame,
        request: CefRequest,
        userGesture: Boolean,
        isRedirect: Boolean,
    ): Boolean = request.url != DiagramWeb.Entry || !frame.isMain || isRedirect

    override fun getResourceRequestHandler(
        browser: CefBrowser,
        frame: CefFrame?,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String,
        disableDefaultHandling: BoolRef,
    ): CefResourceRequestHandlerAdapter? {
        // Blob URLs minted by our renderer stay entirely inside Chromium.
        if (request.url.startsWith("blob:${DiagramWeb.Origin}/")) return null
        disableDefaultHandling.set(true)
        return objectsOnly
    }

    override fun onRenderProcessTerminated(
        browser: CefBrowser,
        status: TerminationStatus,
        errorCode: Int,
        errorString: String,
    ) {
        onFailure(IllegalStateException("Diagram renderer stopped"))
    }

    private val objectsOnly = object : CefResourceRequestHandlerAdapter() {
        override fun getResourceHandler(
            browser: CefBrowser,
            frame: CefFrame?,
            request: CefRequest,
        ): CefResourceHandler {
            val asset = if (request.method == "GET") runBlocking { readDiagramAsset(request.url) } else null
            return DiagramResource(asset)
        }
    }
}

private class DiagramResource(private val asset: DiagramAsset?) : CefResourceHandlerAdapter() {
    private var offset = 0

    override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
        callback.Continue()
        return true
    }

    override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef) {
        response.setStatus(if (asset == null) HTTP_FORBIDDEN else HTTP_OK)
        response.setStatusText(if (asset == null) "Forbidden" else "OK")
        response.setMimeType(asset?.mimeType ?: "text/plain")
        response.setHeaderByName("Content-Security-Policy", DiagramWeb.ContentPolicy, true)
        response.setHeaderByName("X-Content-Type-Options", "nosniff", true)
        responseLength.set(asset?.bytes?.size ?: 0)
    }

    override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
        val bytes = asset?.bytes ?: return false
        val length = minOf(bytes.size - offset, bytesToRead)
        bytes.copyInto(dataOut, startIndex = offset, endIndex = offset + length)
        offset += length
        bytesRead.set(length)
        return length > 0
    }

    override fun cancel() = Unit
}
