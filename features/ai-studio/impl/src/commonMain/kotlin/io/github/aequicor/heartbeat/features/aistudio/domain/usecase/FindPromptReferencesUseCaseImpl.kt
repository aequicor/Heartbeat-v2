package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatHistoryDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.PromptReferenceDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatAttachment
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PromptReference
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class FindPromptReferencesUseCaseImpl(
    private val history: ChatHistoryDataSource,
    private val projects: ProjectDataSource,
    private val attachments: ChatAttachmentDataSource,
    private val references: PromptReferenceDataSource,
) : FindPromptReferencesUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(sessionId: String?, projectId: String?, query: String): Result<List<PromptReference>> =
        runCatching {
            val project = projectId?.let { projects.findById(ProjectId.parse(it)) } as? Project.WithDirectory
            val sessionFiles = if (sessionId == null) emptyList() else findSessionFiles(sessionId, query)
            sessionFiles + references.search(project?.directory, query)
        }.rethrowCancellation()

    @Throwable(Exception::class, CancellationException::class)
    private suspend fun findSessionFiles(sessionId: String, query: String): List<PromptReference> {
        val files = history.files(sessionId).filter { it.name.contains(query, ignoreCase = true) }
            .sortedByDescending { it.name.startsWith(query, ignoreCase = true) }
            .distinctBy { it.storageId ?: it.id }
        val result = linkedMapOf<String, PromptReference>()
        for (file in files) {
            val reference = sessionReference(file) ?: continue
            if (reference.uri !in result) result[reference.uri] = reference
            if (result.size == SessionResultLimit) break
        }
        return result.values.toList()
    }

    @Throwable(CancellationException::class)
    private suspend fun sessionReference(file: ChatAttachment): PromptReference? = runCatching {
        val source = file.source ?: return null
        val uri = attachments.reference(source.location)
        val current = attachments.inspect(uri)
        PromptReference(
            PromptReference.Category.SessionFile, file.name, current.location.originalUri ?: uri,
            current.sizeBytes, image = file.image,
        )
    }.rethrowCancellation().getOrNull()

    private companion object {

        const val SessionResultLimit = 16
    }
}
