package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface SaveEditedImageCopyUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(sessionId: String, file: AttachmentFile, png: ByteArray, thumbnail: String?): Result<String>

    @Throwable(CancellationException::class)
    suspend fun drafts(sessionId: String): Result<List<ChatAttachment>>
}
