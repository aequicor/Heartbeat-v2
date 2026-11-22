package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface ImportChatAttachmentUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(reference: String): Result<ChatAttachment>
}
