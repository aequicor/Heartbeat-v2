package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceReference
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSourceSnapshot
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyHistoryContent

@ConsistentCopyVisibility
public data class AuroraMessageReference internal constructor(internal val snapshot: ChatSourceSnapshot) {

    public val isUser: Boolean get() = snapshot.role == ChatSourceSnapshot.Role.User

    public val fragments: List<Fragment> = snapshot.fragments.map {
        Fragment(it.formattedMarkdown ?: it.text, Format.valueOf(it.format.name))
    }

    // A whole agent message stores the Markdown answer after its work history.
    public val replyPreviewFragment: Fragment?
        get() = fragments.lastOrNull()?.takeIf {
            !isUser && snapshot.source.kind == ChatSourceReference.Kind.Message && it.format == Format.Markdown
        }

    public val attachments: List<Attachment> = snapshot.attachments.map {
        Attachment(it.metadata.name, it.metadata.sizeBytes, it.thumbnail?.let { thumbnail ->
            it.metadata.mimeType?.let { mimeType -> ImageAttachment(mimeType, thumbnail) }
        })
    }

    public val preview: String get() = snapshot.originalText.ifBlank {
        attachments.joinToString { it.name }
    }

    public enum class Format { Plain, Markdown, Code, Diff }

    public data class Fragment(val text: String, val format: Format) {

        public val markdown: ReplyHistoryContent? = text.takeIf { format == Format.Markdown }?.let { ReplyHistoryContent(it, markdown = true) }
    }

    public data class Attachment(val name: String, val size: Long?, val image: ImageAttachment?) {

        public val sizeLabel: String? get() = size?.let(::formatAttachmentSize)
    }
}
