package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PreparedChatSession
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.Throws as Throwable

internal interface ChatEngineDataSource {

    val engines: Flow<List<ChatEngineOptions.Choice>>

    @Throwable(Exception::class, CancellationException::class)
    suspend fun open(projectId: ProjectId, engineId: String, directory: String?): PreparedChatSession

    @Throwable(Exception::class, CancellationException::class)
    suspend fun resume(chatSessionId: String, engineId: String, sessionId: String, directory: String?): ChatEngineConversationDataSource
}
