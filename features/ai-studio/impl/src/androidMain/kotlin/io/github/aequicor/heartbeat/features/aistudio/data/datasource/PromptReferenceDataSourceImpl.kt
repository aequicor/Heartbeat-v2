package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.PromptReferenceDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.net.URLConnection
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class PromptReferenceDataSourceImpl(
    private val context: Context,
    attachments: ChatAttachmentDataSource,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : PromptReferenceDataSource {

    private val scanner = PromptReferenceScanner(attachments, ::children, ::readSkill)

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun search(directory: ProjectDirectory?, query: String) = withContext(ioDispatcher) {
        val project = when (directory) {
            is ProjectDirectory.Local -> Uri.fromFile(File(directory.path.toString())).toString()
            is ProjectDirectory.DocumentTree -> Uri.parse(directory.uri).let {
                DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it)).toString()
            }
            null -> null
        }
        scanner.search(project, Uri.fromFile(File(context.filesDir, ".agents/skills")).toString(), query)
    }

    private fun children(directory: String): List<PromptReferenceEntry> = runCatching {
        val uri = Uri.parse(directory)
        if (uri.scheme == "content") documents(uri) else localFiles(File(requireNotNull(uri.path)))
    }.getOrDefault(emptyList())

    private fun localFiles(directory: File): List<PromptReferenceEntry> =
        directory.listFiles().orEmpty().mapNotNull { file ->
            if (file.canonicalFile != File(directory.canonicalFile, file.name)) return@mapNotNull null
            if (!file.isDirectory && !file.isFile) return@mapNotNull null
            PromptReferenceEntry(
                file.name, Uri.fromFile(file).toString(), file.isDirectory,
                URLConnection.guessContentTypeFromName(file.name), file.length().takeIf { file.isFile },
            )
        }

    private fun documents(directory: Uri): List<PromptReferenceEntry> {
        val path = DocumentsContract.buildChildDocumentsUriUsingTree(directory, DocumentsContract.getDocumentId(directory))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        )
        return context.contentResolver.query(path, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(directory, id)
                    add(PromptReferenceEntry(
                        name, uri.toString(), mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        mime, cursor.getLong(3).takeUnless { cursor.isNull(3) },
                    ))
                }
            }
        }.orEmpty()
    }

    private fun readSkill(uri: String): Map<*, *>? = runCatching {
        val header = context.contentResolver.openInputStream(Uri.parse(uri))?.source()?.buffer()?.use {
            it.request(SkillHeaderBytes)
            skillFrontmatter(it.buffer.readUtf8(minOf(it.buffer.size, SkillHeaderBytes)))
        }
        header?.let { Yaml(SafeConstructor(LoaderOptions())).load<Map<*, *>>(it) }
    }.getOrNull()
}
