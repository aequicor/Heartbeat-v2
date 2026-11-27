package io.github.aequicor.heartbeat.features.aistudio.ui.components

import dev.zacsweers.metro.Inject

@Inject
internal class AttachmentFolderOpenerImpl : AttachmentFolderOpener {

    override val isAvailable: Boolean get() = false

    override suspend fun reveal(originalUri: String?, managedCopyPath: String?): Result<Unit> =
        Result.failure(UnsupportedOperationException("No system file manager can reveal app-private attachments"))
}
