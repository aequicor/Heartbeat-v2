package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.PromptReference
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.FindPromptReferencesUseCase
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ImageAttachment
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.PromptReferences
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal class PromptReferenceSearch(
    private val find: FindPromptReferencesUseCase,
    private val attachmentReferences: AttachmentReferenceResolver?,
    private val screen: MutableStateFlow<AiStudioScreenState>,
    scope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher,
) {

    private val requests = MutableStateFlow<AiStudioAction.SearchPromptReferences?>(null)

    init {
        scope.launch {
            combine(requests, screen, ::lookup).distinctUntilChanged().collectLatest { lookup ->
                if (lookup == null) return@collectLatest
                delay(SearchDebounceMillis)
                withContext(defaultDispatcher) {
                    val drafts = draftEntries(lookup.files, lookup.query)
                    if (drafts.isNotEmpty()) publish(lookup, drafts)
                    val stored = find(lookup.sessionId, lookup.request.projectId, lookup.query)
                        .getOrNull()?.map(PromptReference::display).orEmpty()
                    publish(lookup, (drafts + stored).distinctBy { it.category to it.uri })
                }
            }
        }
    }

    @Throwable(CancellationException::class)
    private suspend fun draftEntries(files: List<File>, query: String): List<PromptReferences.Entry> {
        val resolver = attachmentReferences ?: return emptyList()
        return files.filter { it.name.contains(query, ignoreCase = true) }
            .sortedByDescending { it.name.startsWith(query, ignoreCase = true) }
            .mapNotNull { file ->
                val location = file.attachmentLocation() ?: return@mapNotNull null
                val uri = resolver.resolve(location) ?: return@mapNotNull null
                PromptReferences.Entry(PromptReferences.Category.SessionFile, file.name, uri, file.sizeLabel, file.image)
            }.distinctBy(PromptReferences.Entry::uri)
    }

    private fun publish(lookup: Lookup, entries: List<PromptReferences.Entry>) {
        screen.update { state ->
            if (lookup(requests.value, state) != lookup) return@update state
            val (request, query) = lookup
            state.copy(promptReferences = PromptReferences(request.draftId, request.projectId, query, entries))
        }
    }

    fun request(action: AiStudioAction.SearchPromptReferences) {
        val previous = requests.value
        if (action.query == null) {
            if (previous?.draftId == action.draftId && previous.projectId == action.projectId) {
                requests.value = null
                screen.update { if (it.promptReferences?.draftId == action.draftId) it.copy(promptReferences = null) else it }
            }
            return
        }
        if (previous == action || !action.matches(screen.value)) return
        screen.update { if (action.matches(it)) it.copy(promptReferences = null) else it }
        requests.value = action
    }

    private fun lookup(request: AiStudioAction.SearchPromptReferences?, state: AiStudioScreenState): Lookup? {
        val query = request?.query ?: return null
        if (!request.matches(state)) return null
        val bootstrap = state.sessionBootstrap
        return Lookup(request, query, state.chat?.id.takeIf { bootstrap == null }, bootstrap?.attachments ?: state.attachments)
    }

    private data class Lookup(
        val request: AiStudioAction.SearchPromptReferences,
        val query: String,
        val sessionId: String?,
        val files: List<File>,
    )
}

private fun AiStudioAction.SearchPromptReferences.matches(state: AiStudioScreenState): Boolean {
    val bootstrap = state.sessionBootstrap
    if (bootstrap != null) {
        return draftId == bootstrap.id && projectId == (bootstrap.project as? SessionBootstrapState.Project.Existing)?.id
    }
    return state.chat?.id == draftId && state.chat.projectId == projectId
}

private fun PromptReference.display() = PromptReferences.Entry(
    category = when (category) {
        PromptReference.Category.SessionFile -> PromptReferences.Category.SessionFile
        PromptReference.Category.ProjectFile -> PromptReferences.Category.ProjectFile
        PromptReference.Category.Skill -> PromptReferences.Category.Skill
    },
    name = name,
    uri = uri,
    detail = if (category == PromptReference.Category.Skill) description else sizeBytes?.let(::formatAttachmentSize),
    image = image?.let { ImageAttachment(it.mimeType, it.thumbnail) },
)

private const val SearchDebounceMillis = 100L
