package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.usecase.ReadSubagentHistoryUseCase.Subagent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

internal class ReadSubagentHistoryUseCaseImpl(
    private val conversations: ChatConversationDataSource,
    private val sessions: SessionsDataSource,
    private val projects: ProjectDataSource,
    private val observe: suspend (engineId: String, parentId: String, directory: String?) -> Flow<List<Subagent>>,
) : ReadSubagentHistoryUseCase {

    override fun invoke(chatId: String): Flow<Result<List<Subagent>>> = flow {
        val conversation = conversations.find(chatId) ?: return@flow
        val engineId = conversation.configuration?.engineId ?: return@flow
        val parentId = conversation.engineSessionId ?: return@flow
        emitAll(observe(engineId, parentId, chatDirectory(chatId, sessions, projects)).map { Result.success(it) })
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(Result.failure(failure))
    }
}
