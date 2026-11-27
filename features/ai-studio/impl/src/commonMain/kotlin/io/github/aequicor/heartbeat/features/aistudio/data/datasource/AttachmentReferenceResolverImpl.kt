package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AttachmentReferenceResolver
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class AttachmentReferenceResolverImpl(
    private val attachments: ChatAttachmentDataSource,
) : AttachmentReferenceResolver {

    @Throwable(CancellationException::class)
    override suspend fun resolve(location: AttachmentLocation): String? = runCatching {
        val uri = attachments.reference(location)
        attachments.inspect(uri).location.originalUri ?: uri
    }.rethrowCancellation().getOrNull()
}
