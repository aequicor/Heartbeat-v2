package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface ChatAttachmentDataSource {

    @Throwable(Exception::class, CancellationException::class)
    suspend fun read(reference: String, maxBytes: Int): Pair<String, ByteArray>

    /** Decodes the first frame and returns a PNG thumbnail. */
    @Throwable(Exception::class, CancellationException::class)
    suspend fun imageThumbnail(bytes: ByteArray, format: ChatImage, longestSide: Int): ByteArray? = null
}
