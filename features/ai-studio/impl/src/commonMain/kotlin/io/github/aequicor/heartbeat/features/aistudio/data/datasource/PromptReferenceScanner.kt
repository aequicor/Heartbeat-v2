package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatImage
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PromptReference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

internal const val SkillHeaderBytes = 32_768L

internal class PromptReferenceScanner(
    private val attachments: ChatAttachmentDataSource,
    private val children: (String) -> List<PromptReferenceEntry>,
    private val skillMetadata: (String) -> Map<*, *>?,
) {

    @Throwable(Exception::class, CancellationException::class)
    suspend fun search(project: String?, userSkills: String?, query: String): List<PromptReference> {
        val projectSkills = project?.let { childDirectory(it, ".agents") }?.let { childDirectory(it, "skills") }
        return projectFiles(project, query) + skills(listOfNotNull(projectSkills, userSkills), query)
    }

    private fun childDirectory(parent: String, name: String): String? =
        children(parent).firstOrNull { it.directory && it.name == name }?.uri

    @Throwable(Exception::class, CancellationException::class)
    private suspend fun projectFiles(root: String?, query: String): List<PromptReference> {
        if (root == null) return emptyList()
        val directories = ArrayDeque(listOf(root))
        val visited = mutableSetOf<String>()
        val matches = mutableListOf<PromptReferenceEntry>()
        while (directories.isNotEmpty() && matches.size < MaximumResults) {
            currentCoroutineContext().ensureActive()
            val directory = directories.removeFirst()
            if (!visited.add(directory)) continue
            for (entry in children(directory).sortedBy { it.name.lowercase() }) {
                if (entry.directory) {
                    if (entry.name !in ExcludedDirectories) directories.addLast(entry.uri)
                } else if (entry.name.contains(query, ignoreCase = true)) {
                    matches.add(entry)
                    if (matches.size == MaximumResults) break
                }
            }
        }
        return matches.sortedByDescending { it.name.startsWith(query, ignoreCase = true) }.map {
            PromptReference(PromptReference.Category.ProjectFile, it.name, it.uri, it.sizeBytes, image = thumbnail(it))
        }
    }

    @Throwable(Exception::class, CancellationException::class)
    private suspend fun skills(roots: List<String>, query: String): List<PromptReference> {
        val matches = linkedMapOf<String, PromptReference>()
        for (root in roots) {
            for (folder in children(root).filter(PromptReferenceEntry::directory).sortedBy(PromptReferenceEntry::name)) {
                currentCoroutineContext().ensureActive()
                val skill = children(folder.uri).firstOrNull { !it.directory && it.name == "SKILL.md" } ?: continue
                val metadata = skillMetadata(skill.uri)
                val name = (metadata?.get("name") as? String)?.takeIf { it.isNotBlank() } ?: folder.name
                if (!name.contains(query, ignoreCase = true) || name in matches) continue
                val description = (metadata?.get("description") as? String)?.trim()?.takeIf { it.isNotEmpty() }
                matches[name] = PromptReference(PromptReference.Category.Skill, name, skill.uri, description = description)
                if (matches.size == MaximumResults) return matches.values.toList()
            }
        }
        return matches.values.toList()
    }

    @Throwable(CancellationException::class)
    private suspend fun thumbnail(entry: PromptReferenceEntry): ChatAttachment.Image? {
        val format = ChatImage.forMimeType(entry.mimeType) ?: return null
        if (entry.sizeBytes?.let { it > ChatImage.MaxBytes } == true) return null
        return try {
            val bytes = attachments.read(entry.uri, ChatImage.MaxBytes).second
            val preview = attachments.imageThumbnail(bytes, format, ThumbnailSize) ?: return null
            ChatAttachment.Image(format.mimeType, Base64.encode(preview))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private companion object {

        const val MaximumResults = 16
        const val ThumbnailSize = 96
        val ExcludedDirectories = setOf(".git", ".gradle", ".agents", "node_modules")
    }
}

internal fun skillFrontmatter(text: String): String? {
    val lines = text.trimStart('\uFEFF').lineSequence().iterator()
    if (!lines.hasNext() || lines.next().trim() != "---") return null
    val header = StringBuilder()
    while (lines.hasNext()) {
        val line = lines.next()
        if (line.trim() == "---" || line.trim() == "...") return header.toString()
        header.appendLine(line)
    }
    return null
}
