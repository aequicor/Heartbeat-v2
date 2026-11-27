package io.github.aequicor.heartbeat.features.aistudio.data.dto

import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import kotlinx.serialization.Serializable

@Serializable
internal data class EditedImageCopyDraftDto(
    val id: String,
    val name: String,
    val description: String?,
    val managedPath: String,
    val sizeBytes: Long,
    val thumbnail: String?,
    val kind: String = "PNG · Копия",
) {

    fun toDomain(): ChatAttachment =
        ChatAttachment(
            id = id, name = name, kind = kind, content = "", date = null, storageId = null, size = sizeBytes,
            image = ChatAttachment.Image("image/png", thumbnail), mimeType = "image/png", base64Encoded = true,
            source = AttachmentFile(
                id, name, AttachmentLocation.ManagedCopy(managedPath), description, "image/png", sizeBytes,
            ),
        )

    companion object {

        fun toDto(file: AttachmentFile, sizeBytes: Long, thumbnail: String?, kind: String): EditedImageCopyDraftDto =
            EditedImageCopyDraftDto(
                file.id, file.name, file.description, (file.location as AttachmentLocation.ManagedCopy).relativePath, sizeBytes, thumbnail, kind,
            )
    }
}
