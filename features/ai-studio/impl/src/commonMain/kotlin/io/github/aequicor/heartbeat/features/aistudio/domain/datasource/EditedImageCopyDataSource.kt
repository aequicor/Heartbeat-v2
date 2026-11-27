package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.exception.EditedImageCopySaveException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface EditedImageCopyDataSource {

    @Throwable(EditedImageCopySaveException::class, CancellationException::class)
    suspend fun save(sessionId: String, file: AttachmentFile, png: ByteArray, thumbnail: String?): String

    @Throwable(EditedImageCopySaveException::class, CancellationException::class)
    suspend fun drafts(sessionId: String): List<ChatAttachment>
}
