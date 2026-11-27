package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentCopyTarget
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.source
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

@Inject
internal class ChatAttachmentDataSourceImpl(
    dataDirectory: String,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : ChatAttachmentDataSource {

    private val managed = ManagedAttachmentFiles(dataDirectory.toPath() / "attachments", FileSystem.SYSTEM)

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun copyClipboard(reference: String, target: AttachmentCopyTarget): AttachmentFile =
        withContext(ioDispatcher) {
            val source = attachmentPath(reference)
            managed.store(target, source.fileName.toString(), Files.probeContentType(source), source.toUri().toASCIIString()) {
                Files.newInputStream(source).source()
            }
        }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun reference(location: AttachmentLocation): String = withContext(ioDispatcher) {
        when (location) {
            is AttachmentLocation.Original -> {
                val path = runCatching { Path.of(location.reference) }.getOrNull()
                path?.takeIf { it.isAbsolute }?.normalize()?.toUri()?.toASCIIString() ?: location.reference
            }
            is AttachmentLocation.ManagedCopy -> managed.path(location).toNioPath().toUri().toASCIIString()
        }
    }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun copyToSession(location: AttachmentLocation.ManagedCopy, target: AttachmentCopyTarget) =
        withContext(ioDispatcher) { managed.copyToSession(location, target) }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun discardCopy(location: AttachmentLocation.ManagedCopy) =
        withContext(ioDispatcher) { managed.discard(location) }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun inspect(reference: String): AttachmentFile = withContext(ioDispatcher) {
        val path = attachmentPath(reference)
        AttachmentFile(
            id = Uuid.random().toString(),
            name = path.fileName.toString(),
            location = AttachmentLocation.Original(path.toUri().toASCIIString()),
            mimeType = Files.probeContentType(path),
            sizeBytes = Files.size(path),
        )
    }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun read(reference: String, maxBytes: Int): Pair<String, ByteArray> = withContext(ioDispatcher) {
        val path = attachmentPath(reference)
        path.fileName.toString() to Files.newInputStream(path).source().buffer().use { it.readAttachmentBytes(maxBytes) }
    }

    private fun attachmentPath(reference: String): Path {
        val path = if (reference.startsWith("file:", ignoreCase = true)) Path.of(URI(reference)) else Path.of(reference)
        require(path.isAbsolute && Files.isRegularFile(path))
        return path.normalize()
    }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun imageThumbnail(bytes: ByteArray, format: ChatImage, longestSide: Int): ByteArray =
        withContext(ioDispatcher) {
            if (format == ChatImage.WebP || format == ChatImage.Gif) skiaThumbnail(bytes, longestSide)
            else imageIoThumbnail(bytes, format, longestSide)
        }

    private fun imageIoThumbnail(bytes: ByteArray, format: ChatImage, longestSide: Int): ByteArray {
        val reader = ImageIO.getImageReadersByFormatName(format.extension).next()
        try {
            return MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                reader.setInput(input, false, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(ChatImage.acceptsDimensions(width, height))
                val side = maxOf(width, height)
                val sample = (side + longestSide - 1) / longestSide
                val parameters = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                val thumbnail = reader.read(0, parameters)
                ByteArrayOutputStream().use {
                    check(ImageIO.write(thumbnail, "PNG", it))
                    it.toByteArray()
                }
            }
        } finally {
            reader.dispose()
        }
    }

    // Use the viewer's codec for WebP and for GIF's logical canvas, including offset frames.
    private fun skiaThumbnail(bytes: ByteArray, longestSide: Int): ByteArray =
        Data.makeFromBytes(bytes).use { data ->
            Codec.makeFromData(data).use { codec ->
                require(ChatImage.acceptsDimensions(codec.width, codec.height))
                Bitmap().use { bitmap ->
                    check(bitmap.allocPixels(ImageInfo.makeN32Premul(codec.width, codec.height)))
                    codec.readPixels(bitmap)
                    bitmap.thumbnail(longestSide)
                }
            }
        }

    private fun Bitmap.thumbnail(longestSide: Int): ByteArray {
        val side = maxOf(width, height)
        val target = minOf(longestSide, side)
        val scaledWidth = (width * target / side).coerceAtLeast(1)
        val scaledHeight = (height * target / side).coerceAtLeast(1)
        return Image.makeFromBitmap(this).use { image ->
            Surface.makeRasterN32Premul(scaledWidth, scaledHeight).use { surface ->
                surface.canvas.drawImageRect(image, Rect.makeWH(scaledWidth.toFloat(), scaledHeight.toFloat()))
                surface.makeImageSnapshot().use { thumbnail ->
                    requireNotNull(thumbnail.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
                }
            }
        }
    }
}
