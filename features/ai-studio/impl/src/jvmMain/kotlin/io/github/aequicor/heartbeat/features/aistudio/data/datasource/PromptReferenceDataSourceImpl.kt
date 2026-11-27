package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.PromptReferenceDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class PromptReferenceDataSourceImpl(
    attachments: ChatAttachmentDataSource,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : PromptReferenceDataSource {

    private val scanner = PromptReferenceScanner(attachments, ::children, ::readSkill)

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun search(directory: ProjectDirectory?, query: String) = withContext(ioDispatcher) {
        val project = (directory as? ProjectDirectory.Local)?.path?.toNioPath()?.toUri()?.toASCIIString()
        val userSkills = System.getProperty("user.home")?.toPath()
            ?.resolve(".agents/skills")?.toNioPath()?.toUri()?.toASCIIString()
        scanner.search(project, userSkills, query)
    }

    private fun children(uri: String): List<PromptReferenceEntry> = runCatching {
        Files.newDirectoryStream(Path.of(URI(uri))).use { paths ->
            paths.mapNotNull { path ->
                val attributes = runCatching {
                    Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                }.getOrNull() ?: return@mapNotNull null
                if (!attributes.isDirectory && !attributes.isRegularFile) return@mapNotNull null
                val mimeType = if (attributes.isRegularFile) runCatching { Files.probeContentType(path) }.getOrNull()
                else null
                PromptReferenceEntry(
                    path.fileName.toString(), path.toUri().toASCIIString(), attributes.isDirectory,
                    mimeType, attributes.size().takeIf { attributes.isRegularFile },
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun readSkill(uri: String): Map<*, *>? = runCatching {
        val header = Files.newInputStream(Path.of(URI(uri))).source().buffer().use {
            it.request(SkillHeaderBytes)
            skillFrontmatter(it.buffer.readUtf8(minOf(it.buffer.size, SkillHeaderBytes)))
        }
        header?.let { Yaml(SafeConstructor(LoaderOptions())).load<Map<*, *>>(it) }
    }.getOrNull()
}
