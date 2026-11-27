package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PromptReference
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.FindPromptReferencesUseCase
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ImportChatAttachmentUseCase
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Throwable(CancellationException::class)
internal suspend fun FindPromptReferencesUseCase.importPromptImages(
    chat: AuroraChat,
    text: String,
    attachments: ImportChatAttachmentUseCase,
    resolver: AttachmentReferenceResolver?,
): List<File> {
    val links = LocalPromptLink.findIn(text).filter { link ->
        link.uri.hasImageExtension() || link.label.hasImageExtension()
    }.toList()
    if (links.isEmpty()) return emptyList()

    val existing = mutableSetOf<String>()
    for (file in chat.attachments) {
        file.originalUri?.let(existing::add)
        if (file.managedCopyPath != null) {
            file.attachmentLocation()?.let { resolver?.resolve(it) }?.let(existing::add)
        }
    }
    return buildList {
        for (link in links) {
            if (link.uri in existing) continue
            // A pasted path can reference any local file. Resolve only files offered by this chat's picker.
            val reference = invoke(chat.summary.id, chat.summary.projectId, link.label).getOrNull()?.firstOrNull {
                it.category != PromptReference.Category.Skill && it.uri == link.uri && it.name == link.label
            } ?: continue
            val file = attachments(reference.uri, copyTarget = null).getOrNull()?.display() ?: continue
            if (file.image == null) continue
            existing.add(link.uri)
            add(file.copy(name = reference.name))
        }
    }
}

private fun String.hasImageExtension(): Boolean {
    val extension = substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
    return extension == "jpeg" || ChatImage.entries.any { it.extension == extension }
}
