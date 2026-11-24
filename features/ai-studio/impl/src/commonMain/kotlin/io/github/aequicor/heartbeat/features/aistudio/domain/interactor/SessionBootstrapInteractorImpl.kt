package io.github.aequicor.heartbeat.features.aistudio.domain.interactor

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectsSessionsRepository
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatDraftState
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions.Choice
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.Throws as Throwable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Inject
internal class SessionBootstrapInteractorImpl(
    private val repository: ProjectsSessionsRepository,
    private val projects: ProjectDataSource,
    private val sessions: SessionsDataSource,
    private val runtime: ChatEngineDataSource,
    private val conversations: ChatConversationDataSource,
    private val chatTurns: ChatTurnInteractor,
) : SessionBootstrapInteractor {

    @Throwable(CancellationException::class)
    override suspend fun registerProject(directory: ProjectDirectory, name: String?): Result<ProjectCreationResult> = runCatching {
        val path = (directory as? ProjectDirectory.Local)?.path
        val projectName = (name ?: path?.name).orEmpty().trim()
        when {
            path != null && !isValidName(path.name) -> ProjectCreationResult.InvalidWorkingDirectory
            !isValidName(projectName) -> ProjectCreationResult.InvalidName
            else -> repository.registerProject(projectName, directory)
        }
    }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun withDraft(projectId: ProjectId, use: suspend (ChatCreationDraft) -> Unit): Result<Unit> =
        runCatching {
            useDraft(ChatCreationDraftImpl(projectId, projects, sessions, runtime, repository), use)
        }.rethrowCancellation()

    @Throwable(CancellationException::class)
    override suspend fun withChatConfigurationDraft(
        sessionId: String,
        use: suspend (ChatCreationDraft) -> Unit,
    ): Result<Unit> =
        runCatching {
            val session = sessions.findById(SessionId.parse(sessionId))?.takeIf { it.status == SessionStatus.ACTIVE }
                ?: throw ChatTurnException("Чат недоступен")
            val configuration = conversations.find(sessionId)?.configuration
                ?: throw ChatTurnException("Для чата не выбран движок")
            val engines = runtime.engines.first()
            val engine = engines.firstOrNull { it.id == configuration.engineId }
                ?: Choice(configuration.engineId, configuration.engineId)
            val confirmed = ChatDraftState.Selection(
                engine, configuration.model?.let { Choice(it, it) }, configuration.reasoning?.let { Choice(it, it) },
            )
            useDraft(
                ChatCreationDraftImpl(
                    session.projectId, projects, sessions, runtime, repository,
                    initial = ChatDraftState(engines, confirmed),
                    applyToChat = { chatTurns.applyConfiguration(sessionId, it).getOrThrow() },
                ),
                use,
            )
        }.rethrowCancellation()

    private suspend fun useDraft(draft: ChatCreationDraftImpl, use: suspend (ChatCreationDraft) -> Unit) {
        try {
            coroutineScope {
                val catalog = launch { draft.observeCatalog() }
                try { use(draft) } finally { catalog.cancel() }
            }
        } finally {
            withContext(NonCancellable) { draft.close() }
        }
    }

    private fun isValidName(name: String) = name.isNotBlank() && name != "." && name != ".."
}
