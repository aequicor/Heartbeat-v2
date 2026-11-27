package io.github.aequicor.heartbeat.features.aistudio.domain.usecase

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatHistoryEntry
import kotlinx.coroutines.flow.Flow

internal fun interface ReadSubagentHistoryUseCase {

    operator fun invoke(chatId: String): Flow<Result<List<Subagent>>>

    data class Subagent(
        val sessionId: String,
        val title: String?,
        val description: String?,
        val status: ChatHistoryEntry.StepStatus?,
        val messages: List<ChatHistoryEntry.Message>,
    )
}
