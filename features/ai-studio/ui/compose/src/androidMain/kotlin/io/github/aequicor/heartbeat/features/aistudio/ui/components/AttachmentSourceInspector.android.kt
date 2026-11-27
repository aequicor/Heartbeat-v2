package io.github.aequicor.heartbeat.features.aistudio.ui.components

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class AttachmentSourceInspectorImpl(
    private val context: Context,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : AttachmentSourceInspector {

    @Throwable(CancellationException::class)
    override suspend fun issue(originalUri: String?, managedCopyPath: String?): AttachmentFileIssue? =
        withContext(ioDispatcher) {
            try {
                val file = if (managedCopyPath == null) {
                    requireNotNull(originalUri).let(Uri::parse).also {
                        require(it.scheme == "content" || it.scheme == "file")
                    }
                } else {
                    val directory = File(context.filesDir, "attachments").canonicalFile
                    val managed = File(directory, managedCopyPath).canonicalFile
                    require(managed.path.startsWith(directory.path + File.separator))
                    Uri.fromFile(managed)
                }
                checkNotNull(context.contentResolver.openAssetFileDescriptor(file, "r")).use { }
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: FileNotFoundException) {
                AttachmentFileIssue.Missing
            } catch (_: Exception) {
                AttachmentFileIssue.Unavailable
            }
        }
}
