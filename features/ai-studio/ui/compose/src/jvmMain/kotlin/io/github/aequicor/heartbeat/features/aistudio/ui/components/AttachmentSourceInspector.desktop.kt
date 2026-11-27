package io.github.aequicor.heartbeat.features.aistudio.ui.components

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class AttachmentSourceInspectorImpl(
    private val dataDirectory: String,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : AttachmentSourceInspector {

    @Throwable(CancellationException::class)
    override suspend fun issue(originalUri: String?, managedCopyPath: String?): AttachmentFileIssue? =
        withContext(ioDispatcher) {
            try {
                val file = attachmentPathForReveal(dataDirectory, originalUri, managedCopyPath)
                Files.newByteChannel(file).use { }
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: NoSuchFileException) {
                AttachmentFileIssue.Missing
            } catch (_: Exception) {
                AttachmentFileIssue.Unavailable
            }
        }
}
