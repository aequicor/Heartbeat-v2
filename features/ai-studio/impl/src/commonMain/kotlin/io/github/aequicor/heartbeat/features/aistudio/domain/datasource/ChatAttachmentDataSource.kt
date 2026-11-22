package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface ChatAttachmentDataSource {

    @Throwable(Exception::class, CancellationException::class)
    suspend fun read(reference: String, maxBytes: Int): Pair<String, ByteArray>
}
