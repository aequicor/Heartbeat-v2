package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentCopyTarget
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface ImportChatAttachmentUseCase {

    @Throwable(CancellationException::class)
    suspend operator fun invoke(reference: String, copyTarget: AttachmentCopyTarget?): Result<ChatAttachment>

    @Throwable(CancellationException::class)
    suspend fun copyToSession(location: AttachmentLocation.ManagedCopy, target: AttachmentCopyTarget): Result<AttachmentLocation.ManagedCopy>

    @Throwable(CancellationException::class)
    suspend fun discard(location: AttachmentLocation.ManagedCopy): Result<Unit>
}
