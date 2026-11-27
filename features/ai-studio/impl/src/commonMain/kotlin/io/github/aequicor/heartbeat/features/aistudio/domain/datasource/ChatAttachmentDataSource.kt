package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentCopyTarget
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ChatAttachmentDataSource {

    @Throwable(Exception::class, CancellationException::class)
    suspend fun inspect(reference: String): AttachmentFile

    @Throwable(Exception::class, CancellationException::class)
    suspend fun copyClipboard(reference: String, target: AttachmentCopyTarget): AttachmentFile

    @Throwable(Exception::class, CancellationException::class)
    suspend fun reference(location: AttachmentLocation): String

    @Throwable(Exception::class, CancellationException::class)
    suspend fun copyToSession(location: AttachmentLocation.ManagedCopy, target: AttachmentCopyTarget): AttachmentLocation.ManagedCopy

    @Throwable(Exception::class, CancellationException::class)
    suspend fun discardCopy(location: AttachmentLocation.ManagedCopy)

    @Throwable(Exception::class, CancellationException::class)
    suspend fun read(reference: String, maxBytes: Int): Pair<String, ByteArray>

    /** Decodes the first frame and returns a PNG thumbnail. */
    @Throwable(Exception::class, CancellationException::class)
    suspend fun imageThumbnail(bytes: ByteArray, format: ChatImage, longestSide: Int): ByteArray? = null
}
