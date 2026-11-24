package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatConversationDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatEngineDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.SessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.ChatTurnException
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatConfiguration
import io.github.aequicor.heartbeat.features.aistudio.domain.util.rethrowCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
internal class ApplyChatConfigurationUseCaseImpl(
    private val conversations: ChatConversationDataSource,
    private val engines: ChatEngineDataSource,
    private val sessions: SessionsDataSource,
    private val projects: ProjectDataSource,
) : ApplyChatConfigurationUseCase {

    @Throwable(CancellationException::class)
    override suspend fun invoke(sessionId: String, configuration: ChatConfiguration): Result<Unit> = runCatching {
        val chat = conversations.find(sessionId) ?: throw ChatTurnException("Чат недоступен")
        val previous = chat.configuration ?: throw ChatTurnException("Для чата не выбран движок")
        val engineSessionId = chat.engineSessionId ?: throw ChatTurnException("Сессия движка недоступна")
        if (chat.contextStale || previous.engineId != configuration.engineId) {
            throw ChatTurnException("Движок не может принять контекст этого чата")
        }
        if (previous == configuration) return@runCatching

        val directory = chatDirectory(sessionId, sessions, projects)
        val conversation = engines.resume(previous.engineId, engineSessionId, directory)
        withContext(NonCancellable) {
            val settings = conversation.settings
            val previousEngineSettings = ChatConfiguration(previous.engineId, settings.model?.current, settings.reasoning?.current)
            try {
                conversation.applyConfiguration(configuration)
                if (!conversations.updateConfiguration(sessionId, chat, configuration)) {
                    throw ChatTurnException("Конфигурация чата изменилась")
                }
            } catch (failure: Exception) {
                if (!conversation.rollbackConfiguration(previousEngineSettings, failure)) {
                    try {
                        conversation.close()
                    } catch (closeFailure: Exception) {
                        if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                    }
                }
                throw failure
            }
        }
    }.rethrowCancellation()
}
