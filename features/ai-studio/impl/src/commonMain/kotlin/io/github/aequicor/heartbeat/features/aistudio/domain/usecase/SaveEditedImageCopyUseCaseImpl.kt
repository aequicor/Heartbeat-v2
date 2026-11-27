package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.EditedImageCopyDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class SaveEditedImageCopyUseCaseImpl(private val copies: EditedImageCopyDataSource) : SaveEditedImageCopyUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(sessionId: String, file: AttachmentFile, png: ByteArray, thumbnail: String?): Result<String> =
        runCatching { copies.save(sessionId, file, png, thumbnail) }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun drafts(sessionId: String): Result<List<ChatAttachment>> =
        runCatching { copies.drafts(sessionId) }.rethrowCancellation()
}
