package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentCopyTarget
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPng
import io.github.aequicor.heartbeat.features.aistudio.domain.model.isMarkdownAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

@Inject
internal class ImportChatAttachmentUseCaseImpl(
    private val attachments: ChatAttachmentDataSource,
) : ImportChatAttachmentUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(reference: String, copyTarget: AttachmentCopyTarget?): Result<ChatAttachment> = runCatching {
        val file = if (copyTarget != null) attachments.copyClipboard(reference, copyTarget) else attachments.inspect(reference)
        val format = ChatImage.forMimeType(file.mimeType)
        val thumbnail = if (format != null && file.sizeBytes?.let { it <= ChatImage.MaxBytes } == true) {
            runCatching {
                val (_, bytes) = attachments.read(attachments.reference(file.location), ChatImage.MaxBytes)
                require(bytes.size <= ChatImage.MaxBytes)
                attachments.imageThumbnail(bytes, format, ChatImage.ThumbnailSize)?.let {
                    ChatPng.validate(it, thumbnail = true)
                    Base64.encode(it)
                }
            }.rethrowCancellation().getOrNull()
        } else null

        ChatAttachment(
            id = file.id,
            name = file.name,
            kind = when {
                format != null -> "Изображение"
                isMarkdownAttachment(file.name) -> "Markdown"
                else -> "File"
            },
            content = "",
            date = null,
            storageId = null,
            size = file.sizeBytes,
            image = format?.let { ChatAttachment.Image(it.mimeType, thumbnail) },
            mimeType = file.mimeType,
            base64Encoded = format != null || file.mimeType?.let { !it.startsWith("text/") } == true,
            source = file,
        )
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun copyToSession(location: AttachmentLocation.ManagedCopy, target: AttachmentCopyTarget) =
        runCatching { attachments.copyToSession(location, target) }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun discard(location: AttachmentLocation.ManagedCopy) =
        runCatching { attachments.discardCopy(location) }.rethrowCancellation()

}
