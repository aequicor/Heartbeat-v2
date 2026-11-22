package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

@Inject
internal class ImportChatAttachmentUseCaseImpl(
    private val attachments: ChatAttachmentDataSource,
) : ImportChatAttachmentUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(reference: String): Result<ChatAttachment> = runCatching {
        val (name, bytes) = attachments.read(reference, MaxBytes)
        require(name.isNotBlank() && bytes.size <= MaxBytes)
        val content = bytes.decodeToString(throwOnInvalidSequence = true)
        require('\u0000' !in content)
        val kind = when (name.substringAfterLast('.').lowercase()) {
            "md", "markdown" -> "Markdown"
            else -> "Text"
        }
        ChatAttachment(Uuid.random().toString(), name, kind, content, null, null, bytes.size.toLong())
    }.rethrowCancellation()

    private companion object {

        const val MaxBytes = 1_048_576
    }
}
