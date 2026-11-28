package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentCopyTarget
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentFile
import io.github.aequicor.heartbeat.features.aistudio.domain.model.AttachmentLocation
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.HashingSource
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.blackholeSink
import okio.buffer
import okio.use
import kotlin.uuid.Uuid

internal class ManagedAttachmentFiles(private val root: Path, private val fileSystem: FileSystem) {

    fun store(
        target: AttachmentCopyTarget,
        sourceName: String,
        mimeType: String?,
        originalUri: String?,
        openSource: () -> Source,
    ): AttachmentFile {
        require(target.projectId.matches(StorageKey) && target.scopeId.matches(StorageKey))
        val project = root / target.projectId
        val parent = when {
            target.isDraft -> project / "drafts" / target.scopeId
            target.projectId == ProjectId.Ungrouped.value.toString() -> project / "sessions" / target.scopeId
            else -> project / "files"
        }
        fileSystem.createDirectories(parent)
        val id = Uuid.random().toString()
        val directory = if (target.generatedImage) reserveClipboardImage(parent) else (parent / id).also {
            fileSystem.createDirectory(it, mustCreate = true)
        }
        val name = if (target.generatedImage) "${directory.name}.png" else sourceName
        val suffix = name.substringAfterLast('.', "").takeIf { it.matches(FileExtension) }.orEmpty()
        val physicalName = when {
            target.generatedImage -> name
            suffix.isNotEmpty() -> "attachment.$suffix"
            else -> "attachment"
        }
        val path = directory / physicalName
        val temporary = directory / ".partial"
        try {
            openSource().use { writeCopy(temporary, path, it) }
        } catch (failure: Exception) {
            try {
                fileSystem.delete(temporary)
                fileSystem.delete(directory)
            } catch (cleanup: Exception) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        }
        return AttachmentFile(
            id = id,
            name = name,
            location = AttachmentLocation.ManagedCopy(
                path.relativeTo(root).segments.joinToString("/"),
                originalUri = originalUri.takeUnless { target.generatedImage },
            ),
            mimeType = if (target.generatedImage) "image/png" else mimeType,
            sizeBytes = fileSystem.metadata(path).size,
        )
    }

    fun storeMigrated(
        projectId: String,
        sessionId: String,
        migrationKey: String,
        sourceName: String,
        bytes: ByteArray,
    ): AttachmentLocation.ManagedCopy {
        require(listOf(projectId, sessionId, migrationKey).all { it.matches(StorageKey) })
        val digest = bytes.toByteString().sha256()
        val directory = root / projectId / "sessions" / sessionId / "migration-$migrationKey-${digest.hex()}"
        val suffix = sourceName.substringAfterLast('.', "").takeIf { it.matches(FileExtension) }
        val file = directory / (suffix?.let { "attachment.$it" } ?: "attachment")
        val location = AttachmentLocation.ManagedCopy(file.relativeTo(root).segments.joinToString("/"), originalUri = null)
        if (fileSystem.exists(file)) {
            // A previous startup may have committed the file before SQLite rolled back.
            val existing = HashingSource.sha256(fileSystem.source(path(location)))
            existing.buffer().use { it.readAll(blackholeSink()) }
            check(existing.hash == digest) { "An attachment migration copy has unexpected contents" }
        } else {
            fileSystem.createDirectories(directory)
            require(fileSystem.canonicalize(directory) == fileSystem.canonicalize(root) / directory.relativeTo(root))
            val temporary = directory / ".partial"
            fileSystem.delete(temporary)
            writeCopy(temporary, file, Buffer().write(bytes))
        }
        return location
    }

    private fun writeCopy(temporary: Path, file: Path, source: Source) {
        fileSystem.openReadWrite(temporary, mustCreate = true).use { output ->
            output.sink().buffer().use { it.writeAll(source) }
            output.flush()
        }
        fileSystem.atomicMove(temporary, file)
    }

    fun path(location: AttachmentLocation.ManagedCopy): Path {
        val path = resolve(location)
        require(fileSystem.metadata(path).isRegularFile)
        require(fileSystem.canonicalize(path) == fileSystem.canonicalize(root) / path.relativeTo(root))
        return path
    }

    fun copyToSession(location: AttachmentLocation.ManagedCopy, target: AttachmentCopyTarget): AttachmentLocation.ManagedCopy {
        require(!target.isDraft)
        val source = path(location)
        return store(target, source.name, null, location.originalUri) { fileSystem.source(source) }
            .location as AttachmentLocation.ManagedCopy
    }

    fun discard(location: AttachmentLocation.ManagedCopy) {
        if (fileSystem.metadataOrNull(resolve(location)) == null) return
        val path = path(location)
        val directory = checkNotNull(path.parent)
        fileSystem.delete(path)
        fileSystem.delete(directory)
    }

    private fun resolve(location: AttachmentLocation.ManagedCopy): Path {
        val relative = location.relativePath.toPath()
        val segments = relative.segments
        require(!relative.isAbsolute && segments.none { it == ".." || it == "." })
        require(
            (segments.size == 4 && segments[1] == "files") ||
                (segments.size == 5 && (segments[1] == "drafts" || segments[1] == "sessions")),
        )
        return root / relative
    }

    private fun reserveClipboardImage(parent: Path): Path {
        val used = fileSystem.list(parent).mapNotNull { it.name.removePrefix("clipboard-").toLongOrNull() }
        var index = (used.maxOrNull() ?: 0L) + 1L
        while (true) {
            val path = parent / "clipboard-$index"
            try {
                fileSystem.createDirectory(path, mustCreate = true)
                return path
            } catch (failure: IOException) {
                if (!fileSystem.exists(path)) throw failure
                index++
            }
        }
    }
}

private val StorageKey = Regex("[A-Za-z0-9-]+")
private val FileExtension = Regex("[A-Za-z0-9]{1,16}")
