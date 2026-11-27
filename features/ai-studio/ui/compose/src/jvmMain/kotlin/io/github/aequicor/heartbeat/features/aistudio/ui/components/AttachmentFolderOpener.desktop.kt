package io.github.aequicor.heartbeat.features.aistudio.ui.components

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class AttachmentFolderOpenerImpl(
    private val dataDirectory: String,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : AttachmentFolderOpener {

    override val isAvailable: Boolean by lazy {
        Desktop.isDesktopSupported() && Desktop.getDesktop().let {
            it.isSupported(Desktop.Action.BROWSE_FILE_DIR) || it.isSupported(Desktop.Action.OPEN)
        }
    }

    @Throwable(CancellationException::class)
    override suspend fun reveal(originalUri: String?, managedCopyPath: String?): Result<Unit> = try {
        withContext(ioDispatcher) {
            val path = attachmentPathForReveal(dataDirectory, originalUri, managedCopyPath)
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                desktop.browseFileDirectory(path.toFile())
            } else {
                desktop.open(requireNotNull(path.parent).toFile())
            }
        }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}

@Throwable(IllegalArgumentException::class, IOException::class, URISyntaxException::class, SecurityException::class)
internal fun attachmentPathForReveal(dataDirectory: String, originalUri: String?, managedCopyPath: String?): Path {
    val file = if (managedCopyPath == null) {
        val uri = URI(requireNotNull(originalUri))
        require(uri.scheme.equals("file", ignoreCase = true))
        Path.of(uri)
    } else {
        val directory = Path.of(dataDirectory).resolve("attachments").toRealPath()
        val relative = Path.of(managedCopyPath)
        require(!relative.isAbsolute && relative.none { it.toString() == ".." || it.toString() == "." })
        directory.resolve(relative).toRealPath().also {
            require(it.startsWith(directory))
        }
    }
    require(file.isAbsolute && Files.readAttributes(file, BasicFileAttributes::class.java).isRegularFile)
    return file
}
