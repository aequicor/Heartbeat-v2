package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPng
import io.github.aequicor.heartbeat.features.aistudio.domain.model.isMarkdownAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

@Inject
internal class ImportChatAttachmentUseCaseImpl(
    private val attachments: ChatAttachmentDataSource,
) : ImportChatAttachmentUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(reference: String): Result<ChatAttachment> = runCatching {
        val (name, bytes) = attachments.read(reference, ChatImage.MaxBytes)
        require(name.isNotBlank() && bytes.size <= ChatImage.MaxBytes)
        val image = ChatImage.identify(bytes)
        if (image != null) {
            if (image == ChatImage.Png) ChatPng.validate(bytes)
            val thumbnail = requireNotNull(attachments.imageThumbnail(bytes, image, ChatImage.ThumbnailSize))
            ChatPng.validate(thumbnail, thumbnail = true)
            return@runCatching ChatAttachment(
                Uuid.random().toString(), name, "Изображение", Base64.encode(bytes), null, null, bytes.size.toLong(),
                ChatAttachment.Image(image.mimeType, Base64.encode(thumbnail)),
            )
        }
        val content = bytes.decodeToString(throwOnInvalidSequence = true)
        require('\u0000' !in content)
        val kind = if (isMarkdownAttachment(name)) "Markdown" else "Text"
        ChatAttachment(Uuid.random().toString(), name, kind, content, null, null, bytes.size.toLong())
    }.rethrowCancellation()

}
