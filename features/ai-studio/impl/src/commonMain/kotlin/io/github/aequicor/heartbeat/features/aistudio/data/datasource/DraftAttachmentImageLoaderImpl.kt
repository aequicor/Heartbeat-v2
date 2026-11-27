package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.features.aistudio.attachmentImagePreviewLimit
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.DraftAttachmentImageLoader
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.attachmentLocation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

@Inject
internal class DraftAttachmentImageLoaderImpl(
    private val attachments: ChatAttachmentDataSource,
    @Named("studio-default") private val defaultDispatcher: CoroutineDispatcher,
) : DraftAttachmentImageLoader {

    @Throwable(CancellationException::class)
    override suspend fun load(file: File): Result<File> = withContext(defaultDispatcher) {
        runCatching {
            if (file.imageBytes() != null) return@runCatching file
            val location = checkNotNull(file.attachmentLocation())
            val limit = attachmentImagePreviewLimit(file.kind)
            val bytes = attachments.read(attachments.reference(location), limit).second
            require(bytes.size <= limit)
            val format = checkNotNull(ChatImage.identify(bytes))
            file.copy(
                content = Base64.encode(bytes),
                image = ImageAttachment(format.mimeType, file.image?.thumbnail),
                mimeType = format.mimeType,
                base64Encoded = true,
            )
        }.rethrowCancellation()
    }
}
