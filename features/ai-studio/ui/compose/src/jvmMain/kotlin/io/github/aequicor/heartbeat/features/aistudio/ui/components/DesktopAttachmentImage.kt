package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbMenu
import io.github.aequicor.heartbeat.designsystem.components.HbMenuPresentation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JComponent
import kotlin.math.roundToInt

internal class AttachmentImageTransform {

    var viewport by mutableStateOf(IntSize.Zero)

    var pan by mutableStateOf(Offset.Zero)

    private var zoom by mutableStateOf<Float?>(null)

    fun scale(bitmap: ImageBitmap?): Float {
        zoom?.let { return it }
        if (bitmap == null || viewport.width == 0 || viewport.height == 0) return 1f
        return minOf(1f, viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
    }

    fun changeScale(factor: Float, bitmap: ImageBitmap?, anchor: Offset = pan) {
        val previous = scale(bitmap)
        val next = (previous * factor).coerceIn(0.025f, 8f)
        if (next == previous) return
        pan += (anchor - pan) * (1f - next / previous)
        zoom = next
    }

    fun resetToActualSize() {
        zoom = 1f
        pan = Offset.Zero
    }

    fun imageRect(bitmap: ImageBitmap): IntRect {
        val scale = scale(bitmap)
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val left = ((viewport.width - width) / 2f + pan.x).roundToInt()
        val top = ((viewport.height - height) / 2f + pan.y).roundToInt()
        return IntRect(left, top, left + width, top + height)
    }
}

@Composable
@Suppress("InjectDispatcher") // Decode only for the lifetime of this preview.
internal fun rememberAttachmentPreviewBitmap(file: File): Result<ImageBitmap>? {
    val result by produceState<Result<ImageBitmap>?>(null, file) {
        if (file.image != null) {
            value = withContext(Dispatchers.Default) {
                runCatching { decodeAttachmentPreview(requireNotNull(file.imageBytes()), requireNotNull(file.image)) }
            }
        }
    }
    return result
}

@Composable
internal fun AttachmentImageViewport(
    name: String,
    bitmap: Result<ImageBitmap>?,
    transform: AttachmentImageTransform,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
    gestureTarget: JComponent?,
) {
    val image = bitmap?.getOrNull()
    if (image == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            AuroraText(if (bitmap?.isFailure == true) "Не удалось открыть изображение" else "Загрузка изображения…")
        }
        return
    }
    var menuAnchor by remember(transform) { mutableStateOf<IntOffset?>(null) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current.density
    DisposableEffect(gestureTarget, transform, image, density) {
        val removeListener = gestureTarget?.let { target ->
            addMacAttachmentMagnificationListener(target) { factor, point ->
                val layout = coordinates?.takeIf { it.isAttached } ?: return@addMacAttachmentMagnificationListener
                val position = layout.windowToLocal(Offset(point.x * density, point.y * density))
                val viewport = transform.viewport
                if (position.x in 0f..<viewport.width.toFloat() && position.y in 0f..<viewport.height.toFloat()) {
                    val center = Offset(viewport.width / 2f, viewport.height / 2f)
                    transform.changeScale(factor, image, position - center)
                }
            }
        }
        onDispose { removeListener?.invoke() }
    }
    HbMenu(
        expanded = menuAnchor != null,
        onDismissRequest = { menuAnchor = null },
        actions = listOf(attachmentCopyAction(onCopy)),
        modifier = modifier.padding(16.dp),
        presentation = HbMenuPresentation.AuroraFileActions,
        restoreTriggerFocus = false,
        pointerAnchor = menuAnchor,
    ) { trigger ->
        Canvas(
            trigger.fillMaxSize().clipToBounds()
                .semantics { contentDescription = name }
                .onSizeChanged { transform.viewport = it }
                .onGloballyPositioned { coordinates = it }
                .controlWheelZoom { factor, anchor -> transform.changeScale(factor, image, anchor) }
                .pointerInput(transform, image) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                            val change = event.changes.firstOrNull { !it.isConsumed } ?: continue
                            val anchor = IntOffset(change.position.x.roundToInt(), change.position.y.roundToInt())
                            if (!transform.imageRect(image).contains(anchor)) continue
                            change.consume()
                            menuAnchor = anchor
                        }
                    }
                }
                .pointerInput(transform) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        transform.pan += delta
                    }
                },
        ) {
            val rect = transform.imageRect(image)
            drawImage(image = image, dstOffset = rect.topLeft, dstSize = rect.size)
        }
    }
}
