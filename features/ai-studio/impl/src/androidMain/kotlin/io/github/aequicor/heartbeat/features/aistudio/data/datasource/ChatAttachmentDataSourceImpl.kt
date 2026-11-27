package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentCopyTarget
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.source
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

@Inject
internal class ChatAttachmentDataSourceImpl(
    private val context: Context,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : ChatAttachmentDataSource {

    private val managedRoot = context.filesDir.absolutePath.toPath() / "attachments"
    private val managed = ManagedAttachmentFiles(managedRoot, FileSystem.SYSTEM)

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun copyClipboard(reference: String, target: AttachmentCopyTarget): AttachmentFile =
        withContext(ioDispatcher) {
            val uri = Uri.parse(reference)
            if (uri.scheme == ContentResolver.SCHEME_FILE && target.generatedImage) {
                val directory = File(context.cacheDir, "attachment-edits").canonicalFile
                val staged = File(requireNotNull(uri.path)).canonicalFile
                require(staged.parentFile == directory && staged.isFile)
                return@withContext managed.store(target, staged.name, ChatImage.Png.mimeType, null) {
                    staged.source()
                }
            }
            require(uri.scheme == ContentResolver.SCHEME_CONTENT)
            val original = inspectContent(uri)
            managed.store(target, original.name, original.mimeType, uri.toString()) {
                checkNotNull(context.contentResolver.openInputStream(uri)).source()
            }
        }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun reference(location: AttachmentLocation): String = withContext(ioDispatcher) {
        when (location) {
            is AttachmentLocation.Original -> File(location.reference).takeIf { it.isAbsolute }
                ?.let { Uri.fromFile(it).toString() } ?: location.reference
            is AttachmentLocation.ManagedCopy -> Uri.fromFile(managed.path(location).toFile()).toString()
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
        val uri = Uri.parse(reference)
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = managedPath(uri)
            return@withContext AttachmentFile(
                Uuid.random().toString(), path.name, AttachmentLocation.Original(reference),
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.name.substringAfterLast('.', "").lowercase()),
                sizeBytes = FileSystem.SYSTEM.metadata(path).size,
            )
        }
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val file = inspectContent(uri)
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        file
    }

    private fun inspectContent(uri: Uri): AttachmentFile {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        val file = checkNotNull(resolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
        )?.use { cursor ->
            check(cursor.moveToFirst())
            val name = checkNotNull(cursor.getString(0))
            require(name.isNotBlank())
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else null
            AttachmentFile(
                Uuid.random().toString(), name, AttachmentLocation.Original(uri.toString()),
                mimeType = mimeType, sizeBytes = size?.takeIf { it >= 0 },
            )
        })
        return file
    }
    @Throwable(Exception::class, CancellationException::class)
    override suspend fun read(reference: String, maxBytes: Int): Pair<String, ByteArray> = withContext(ioDispatcher) {
        val uri = Uri.parse(reference)
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = managedPath(uri)
            return@withContext path.name to FileSystem.SYSTEM.source(path).buffer().use { it.readAttachmentBytes(maxBytes) }
        }
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        require(!name.isNullOrBlank())
        val stream = requireNotNull(resolver.openInputStream(uri))
        name to stream.source().buffer().use { it.readAttachmentBytes(maxBytes) }
    }

    private fun managedPath(uri: Uri): okio.Path {
        val path = checkNotNull(uri.path).toPath()
        val relative = path.relativeTo(managedRoot)
        return managed.path(AttachmentLocation.ManagedCopy(relative.toString()))
    }

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun imageThumbnail(bytes: ByteArray, format: ChatImage, longestSide: Int): ByteArray = withContext(ioDispatcher) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outMimeType == format.mimeType && ChatImage.acceptsDimensions(options.outWidth, options.outHeight))
        val side = maxOf(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        options.inSampleSize = Integer.highestOneBit((side + longestSide - 1) / longestSide)
        val thumbnail = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
        try {
            ByteArrayOutputStream().use {
                check(thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally {
            thumbnail.recycle()
        }
    }
}
