package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal sealed interface AttachmentLocation {

    val originalUri: String?

    data class Original(val reference: String) : AttachmentLocation {

        override val originalUri: String get() = reference
    }

    data class ManagedCopy(
        val relativePath: String,
        override val originalUri: String? = null,
    ) : AttachmentLocation
}
