package io.github.aequicor.heartbeat.features.aistudio.ui.components

import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

/** Opens the native file manager at the attachment's local file. */
internal interface AttachmentFolderOpener {

    val isAvailable: Boolean

    @Throwable(CancellationException::class)
    suspend fun reveal(originalUri: String?, managedCopyPath: String?): Result<Unit>
}
