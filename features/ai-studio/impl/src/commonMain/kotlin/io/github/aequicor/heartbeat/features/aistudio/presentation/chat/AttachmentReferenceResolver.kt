package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal fun interface AttachmentReferenceResolver {

    @Throwable(CancellationException::class)
    suspend fun resolve(location: AttachmentLocation): String?
}

@Throwable(CancellationException::class)
internal suspend fun AttachmentReferenceResolver.rebindLinks(prompt: String, moves: List<Pair<File, File>>): String {
    if ("](<" !in prompt) return prompt
    var text = prompt
    for ((before, after) in moves) {
        val oldLocation = before.attachmentLocation() ?: continue
        val newLocation = after.attachmentLocation() ?: continue
        val oldUri = resolve(oldLocation) ?: continue
        val target = "](<$oldUri>)"
        if (target !in text) continue
        val uri = resolve(newLocation) ?: continue
        text = text.replace(target, "](<$uri>)")
    }
    return text
}
