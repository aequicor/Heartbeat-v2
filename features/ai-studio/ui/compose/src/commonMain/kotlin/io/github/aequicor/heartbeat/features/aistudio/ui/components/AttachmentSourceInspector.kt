package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalWindowInfo
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface AttachmentSourceInspector {

    @Throwable(CancellationException::class)
    suspend fun issue(originalUri: String?, managedCopyPath: String?): AttachmentFileIssue?
}

internal val LocalAttachmentSourceInspector = staticCompositionLocalOf<AttachmentSourceInspector?> { null }
internal val LocalAttachmentLoadFailures = staticCompositionLocalOf<Set<String>> { emptySet() }
internal val LocalRemovedAttachmentLinks = staticCompositionLocalOf<Set<String>> { emptySet() }

@Composable
internal fun attachmentFileIssue(originalUri: String?, managedCopyPath: String?, loadFailed: Boolean): AttachmentFileIssue? {
    val inspector = LocalAttachmentSourceInspector.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val sourceIssue by key(inspector, originalUri, managedCopyPath) {
        produceState<AttachmentFileIssue?>(null, focused) {
            if (inspector != null && (originalUri != null || managedCopyPath != null)) {
                value = inspector.issue(originalUri, managedCopyPath)
            }
        }
    }
    return sourceIssue ?: AttachmentFileIssue.LoadFailed.takeIf { loadFailed }
}

@Composable
internal fun messageAttachmentSourceIssue(file: File): AttachmentFileIssue? {
    if (file.openId in LocalRemovedAttachmentLinks.current) return AttachmentFileIssue.RemovedFromCatalog
    return attachmentFileIssue(file.originalUri, file.managedCopyPath, file.openId in LocalAttachmentLoadFailures.current)
}

internal fun AttachmentFileIssue.blocksPreview(file: File): Boolean =
    file.deferredReading || this == AttachmentFileIssue.LoadFailed
