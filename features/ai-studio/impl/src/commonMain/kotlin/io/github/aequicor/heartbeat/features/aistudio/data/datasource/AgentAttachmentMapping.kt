package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallKind
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolCallStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.ToolOutput
import io.github.aequicor.heartbeat.features.aistudio.MaxAttachmentImagePreviewBytes
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentTransferMode
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatPng
import okio.Buffer
import okio.Path.Companion.toPath
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

private const val MaxArtifactBytes = ChatImage.MaxBytes

@Throwable(CancellationException::class)
internal suspend fun ChatAttachmentDataSource.receiveAgentAttachment(content: SessionContent, directory: String?): ChatAttachment? =
    when (content) {
        is SessionContent.Artifact -> receiveArtifact(content, directory)
        is SessionContent.Resource -> receiveReference(content, directory)
        is SessionContent.Text -> null
    }

@Throwable(CancellationException::class)
private suspend fun ChatAttachmentDataSource.receiveArtifact(
    artifact: SessionContent.Artifact,
    directory: String?,
): ChatAttachment? {
    val payload = artifact.payload
    val encoded = payload is SessionContent.Artifact.Payload.Base64
    val declaredType = artifact.mimeType?.substringBefore(';')?.trim()?.lowercase()?.takeUnless { it.isBlank() }
        ?: if (encoded) "application/octet-stream" else "text/plain"
    val content: String
    val bytes: ByteArray
    val imageType = ChatImage.forMimeType(declaredType)
    val limit = if (encoded && imageType != null) MaxAttachmentImagePreviewBytes else MaxArtifactBytes
    when (payload) {
        is SessionContent.Artifact.Payload.Text -> {
            content = payload.text
            if (content.length > MaxArtifactBytes) return null
            bytes = content.encodeToByteArray()
        }
        is SessionContent.Artifact.Payload.Base64 -> {
            content = payload.data
            if (imageType != null && content.length > (MaxArtifactBytes + 2) / 3 * 4) {
                artifact.uri?.takeUnless { it.startsWith("data:", ignoreCase = true) }?.let { uri ->
                    val link = receiveReference(SessionContent.Resource(uri, declaredType, null), directory)
                    if (link?.image != null) return link
                }
            }
            if (content.length > (limit + 2) / 3 * 4) return null
            bytes = try {
                Base64.decode(content)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
    }
    if (bytes.size > limit) return null
    val format = ChatImage.identify(bytes).takeIf { encoded }
    val mimeType = format?.mimeType ?: declaredType
    val reference = artifact.uri?.takeUnless { it.startsWith("data:", ignoreCase = true) }
        ?.let { agentFileReference(it, directory) }
        ?.takeIf { it.isLocalAgentFile() }
    val sourceFile = reference?.let { inspectAgentAttachment(it) }
    val name = sourceFile?.name ?: artifact.fileName(format)
    val image = if (format != null) {
        try {
            if (format == ChatImage.Png && bytes.size <= MaxArtifactBytes) ChatPng.validate(bytes)
            val thumbnail = imageThumbnail(bytes, format, ChatImage.ThumbnailSize)
            thumbnail?.let { ChatAttachment.Image(mimeType, Base64.encode(it)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    } else null
    if (bytes.size > MaxArtifactBytes && image == null) return null
    // ACP content blocks have no attachment ID. The source and original bytes identify
    // a replay even when the agent changes between text and Base64 resource blocks.
    val source = sourceFile?.location?.originalUri ?: reference ?: artifact.fileReference() ?: name
    val id = "artifact-" + Buffer().writeUtf8(source).writeByte(0).write(bytes).readByteString().sha256().hex()
    return ChatAttachment(
        id, name, attachmentKind(name), content, null, null, bytes.size.toLong(),
        image = image, mimeType = mimeType, base64Encoded = encoded,
        source = reference?.let {
            AttachmentFile(id, name, sourceFile?.location ?: AttachmentLocation.Original(it), mimeType = mimeType,
                sizeBytes = sourceFile?.sizeBytes ?: bytes.size.toLong())
        },
    )
}

@Throwable(CancellationException::class)
private suspend fun ChatAttachmentDataSource.receiveReference(
    content: SessionContent.Resource,
    directory: String?,
): ChatAttachment? {
    val original = content.uri.takeUnless { it.isBlank() } ?: return null
    val reference = agentFileReference(original, directory)
    val inspected = reference.takeIf { it.isLocalAgentFile() }?.let { inspectAgentAttachment(it) }
    val location = inspected?.location ?: AttachmentLocation.Original(reference)
    val uri = location.originalUri ?: reference
    val fileName = inspected?.name ?: original.substringBefore('?').substringBefore('#').displayFileName()
    val mime = inspected?.mimeType ?: content.mimeType
    val title = content.title?.displayFileName()
    val name = if (fileName != null && mime?.startsWith("image/", ignoreCase = true) == true &&
        fileName.isGeneratedImageStorageName() && title != null && !title.isGeneratedImageStorageName()
    ) {
        val extension = fileName.substringAfterLast('.', "")
        if (extension.isEmpty() || title.endsWith(".$extension", ignoreCase = true)) title else "$title.$extension"
    } else inspected?.name ?: title ?: fileName ?: "Вложение"
    val id = "reference-" + Buffer().writeUtf8(uri).readByteString().sha256().hex()
    val image = thumbnailForReference(uri, inspected, mime)
    return ChatAttachment(
        id, name, attachmentKind(fileName ?: name), "", null, null, inspected?.sizeBytes,
        image = image, mimeType = mime,
        base64Encoded = mime?.startsWith("text/") == false,
        source = AttachmentFile(id, name, location, mimeType = mime, sizeBytes = inspected?.sizeBytes),
        transferMode = AttachmentTransferMode.ResourceLink,
    )
}

internal fun String.isGeneratedImageStorageName(): Boolean =
    GeneratedImageStorageName.matches(substringBeforeLast('.'))

private val GeneratedImageStorageName = Regex(
    "(?:exec-)?[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",
    RegexOption.IGNORE_CASE,
)

@Throwable(CancellationException::class)
private suspend fun ChatAttachmentDataSource.inspectAgentAttachment(reference: String): AttachmentFile? = try {
    inspect(reference)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

@Throwable(CancellationException::class)
private suspend fun ChatAttachmentDataSource.thumbnailForReference(
    uri: String, file: AttachmentFile?, mime: String?,
): ChatAttachment.Image? {
    val format = ChatImage.forMimeType(mime) ?: return null
    if (file?.sizeBytes?.let { it <= MaxAttachmentImagePreviewBytes } != true) return null
    return try {
        val bytes = read(uri, MaxAttachmentImagePreviewBytes).second
        if (bytes.size > MaxAttachmentImagePreviewBytes || ChatImage.identify(bytes) != format) return null
        val thumbnail = imageThumbnail(bytes, format, ChatImage.ThumbnailSize) ?: return null
        ChatAttachment.Image(format.mimeType, Base64.encode(thumbnail))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private fun attachmentKind(name: String): String = when (val extension = name.substringAfterLast('.', "").lowercase()) {
    "md", "markdown" -> "Markdown"
    "diff", "patch" -> "Патч"
    "" -> "Файл"
    else -> extension.uppercase()
}

@Throwable(CancellationException::class)
private suspend fun ChatAttachmentDataSource.agentFileReference(value: String, directory: String?): String {
    if (UriScheme.containsMatchIn(value)) return value
    val path = runCatching { value.toPath() }.getOrNull() ?: return value
    val absolute = if (path.isAbsolute) path.normalized() else {
        val base = directory?.let { runCatching { it.toPath() }.getOrNull() }?.takeIf { it.isAbsolute } ?: return value
        base.resolve(path, normalize = true)
    }
    return try {
        reference(AttachmentLocation.Original(absolute.toString()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        absolute.toString()
    }
}

private val UriScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]+:")

private fun String.isLocalAgentFile(): Boolean =
    startsWith("file:", ignoreCase = true) || startsWith("content:", ignoreCase = true) ||
        runCatching { toPath().isAbsolute }.getOrDefault(false)

internal fun SessionTimelineItem.ToolCall.attachmentContent(output: ToolOutput): SessionContent? = when (output) {
    is ToolOutput.Content -> when (val content = output.value) {
        is SessionContent.Artifact -> withReadSource(content)
        is SessionContent.Resource -> content
        is SessionContent.Text -> null
    }
    is ToolOutput.Diff -> if (kind == ToolCallKind.EDIT && status == ToolCallStatus.COMPLETED &&
        output.scope == ToolOutput.DiffScope.File && output.oldText == null
    ) SessionContent.Resource(output.path, null, null) else null
    is ToolOutput.Command, is ToolOutput.Terminal -> null
}

internal fun SessionContent.Artifact.fileName(format: ChatImage? = null): String {
    fileReference()?.displayFileName()?.let { return it }
    val type = mimeType?.substringBefore(';')?.lowercase()
    val extension = (format ?: ChatImage.forMimeType(type))?.extension ?: when (type) {
        "text/plain" -> "txt"
        "text/markdown" -> "md"
        "application/pdf" -> "pdf"
        else -> "bin"
    }
    return "Вложение.$extension"
}

private fun String.displayFileName(): String? =
    substringAfterLast('/').substringAfterLast('\\').filterNot { it.isISOControl() || it in "<>:\"|?*" }
        .take(180).trim().trimEnd('.').takeIf { it.isNotBlank() }

private fun SessionContent.Artifact.fileReference(): String? =
    uri?.takeUnless { it.startsWith("data:", ignoreCase = true) }?.substringBefore('?')?.substringBefore('#')

private fun SessionTimelineItem.ToolCall.withReadSource(artifact: SessionContent.Artifact): SessionContent.Artifact {
    val path = inputPath ?: paths.singleOrNull()
    return if (path != null && kind == ToolCallKind.READ && artifact.uri == null &&
        artifact.mimeType?.startsWith("image/", ignoreCase = true) == true
    ) artifact.copy(uri = path) else artifact
}
