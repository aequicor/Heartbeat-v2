package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class PrepareChatContextUseCaseImpl(
    private val conversations: ChatConversationDataSource,
    private val engines: ChatEngineDataSource,
    private val sessions: SessionsDataSource,
    private val projects: ProjectDataSource,
) : PrepareChatContextUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(sessionId: String): Result<ChatEngineConversationDataSource> = runCatching {
        val chat = conversations.find(sessionId)
            ?: throw ChatTurnException("Чат не найден или перемещён в архив")
        if (chat.contextStale) throw ChatTurnException("Контекст чата требует восстановления")
        val configuration = chat.configuration ?: throw ChatTurnException("Для чата не выбран движок")
        val engineSessionId = chat.engineSessionId ?: throw ChatTurnException("Сессия движка недоступна")
        val directory = chatDirectory(sessionId, sessions, projects)
        val conversation = engines.resume(configuration.engineId, engineSessionId, directory)
        val settings = conversation.settings
        val previous = ChatConfiguration(configuration.engineId, settings.model?.current, settings.reasoning?.current)
        try {
            conversation.applyConfiguration(configuration)
        } catch (failure: Exception) {
            withContext(NonCancellable) { conversation.rollbackConfiguration(previous, failure) }
            throw failure
        }
        conversation
    }.rethrowCancellation()
}

internal suspend fun ChatEngineConversationDataSource.rollbackConfiguration(
    previous: ChatConfiguration,
    failure: Exception,
): Boolean = try {
    applyConfiguration(previous)
    true
} catch (rollbackFailure: Exception) {
    if (rollbackFailure !== failure) failure.addSuppressed(rollbackFailure)
    false
}

internal suspend fun chatDirectory(
    sessionId: String,
    sessions: SessionsDataSource,
    projects: ProjectDataSource,
): String? {
    val session = sessions.findById(SessionId.parse(sessionId))
        ?.takeIf { it.status == SessionStatus.ACTIVE } ?: throw ChatTurnException("Чат недоступен")
    return when (val project = projects.findById(session.projectId)) {
        is Project.WithDirectory -> project.workingDirectory?.toString()
            ?: throw ChatTurnException("Рабочая папка недоступна движку")
        is Project.WithoutDirectory -> null
        null -> throw ChatTurnException("Проект недоступен")
    }
}

internal suspend fun ChatEngineConversationDataSource.applyConfiguration(
    configuration: ChatConfiguration,
) {
    restoreOption(settings.model, configuration.model)
    restoreOption(settings.reasoning, configuration.reasoning)
    val actual = settings
    val reasoningMatches = configuration.reasoning == null || actual.reasoning?.current == configuration.reasoning
    if (actual.model?.current != configuration.model || !reasoningMatches) {
        throw ChatTurnException("Движок отклонил настройки чата")
    }
}

private suspend fun ChatEngineConversationDataSource.restoreOption(
    selector: ChatEngineOptions.Selector?,
    value: String?,
) {
    if (value == null) return
    if (selector == null || selector.choices.none { it.id == value }) {
        throw ChatTurnException("Сохранённый параметр чата больше недоступен")
    }
    val applied = select(selector.id, value)
    if (listOfNotNull(applied.model, applied.reasoning).none { it.id == selector.id && it.current == value }) {
        throw ChatTurnException("Движок отклонил сохранённый параметр чата")
    }
}
